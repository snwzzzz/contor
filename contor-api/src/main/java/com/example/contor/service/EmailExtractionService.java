package com.example.contor.service;

import com.example.contor.ai.EmailContent;
import com.example.contor.ai.ExtractionService;
import com.example.contor.ai.JsonSchemas;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.RecordDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.Slug;
import com.example.contor.fieldtype.FieldTypeRegistry;
import com.example.contor.persistence.ProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads one email with the extraction model and turns it into CREATE_RECORD / UPDATE_RECORD drafts for the collections
 * the AI may write to. The answer is constrained to a JSON schema; every value is still checked with the field's
 * {@code FieldTypeHandler} (invalid values are dropped, an item missing a required value is dropped). Duplicates are
 * avoided: an existing record (match keys, else title) gets an UPDATE_RECORD with only the values it lacks, and an
 * item that a pending proposal already covers is skipped.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmailExtractionService implements ExtractionService {

    public static final String PROMPT_VERSION = "extract-v1";

    static final int MAX_BODY_CHARS = 8000;

    /** The AI never sets references or files. */
    private static final Set<CollectionFieldType> SKIPPED_TYPES = Set.of(CollectionFieldType.RELATION, CollectionFieldType.FILE);

    private static final String SYSTEM = """
            You are the assistant inside contor, a personal management app. You read one email of the user and \
            extract what is worth recording into the user's collections. Answer with JSON only.
            Today is %s (%s). The user's time zone is %s. The user's own address is %s: never record the user.
            Rules:
            - Record only facts stated in the email. Leave a field out instead of guessing.
            - people: individual humans the user deals with (a personal sender, people named with contact details). \
            Never companies, newsletters, no-reply or system senders.
            - organizations: companies, shops, employers, authorities, schools the user actually deals with.
            - events: appointments, meetings, interviews, deadlines with a date, bookings, trips. starts_at is required.
            - tasks: concrete things the user has to do (reply, pay, sign, upload, send, prepare), with due_date if stated.
            - any other collection: only when the email is clearly about such a thing.
            - title: short and specific, e.g. a person's full name, "Interview at Acme", "Pay invoice 2024-118".
            - DATE as YYYY-MM-DD; DATETIME as ISO-8601 with offset in the user's time zone unless the email names \
            another zone; MONEY as {"amount": 12.5, "currency": "EUR"}; ENUM and STATUS only with the listed values.
            - confidence between 0 and 1; reason: one short sentence quoting the evidence.
            - Advertising, newsletters and notifications without anything to record: return no items.""";

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final FieldTypeRegistry fieldTypeRegistry;
    private final RecordService recordService;
    private final ProposalRepository proposalRepository;
    private final Clock clock;

    @Override
    public List<ProposalDraft> analyze(EmailContent email, List<CollectionDto> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        Map<String, CollectionDto> bySlug = new LinkedHashMap<>();
        targets.forEach(collection -> bySlug.put(collection.getSlug(), collection));

        LocalDate today = LocalDate.now(clock.withZone(properties.timezone()));
        String system = SYSTEM.formatted(today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                properties.timezone(), email.mailbox());
        Map<String, Object> schema = JsonSchemas.object(Map.of("items", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                "collection", JsonSchemas.oneOf(List.copyOf(bySlug.keySet())),
                "title", JsonSchemas.string(),
                "values", JsonSchemas.freeObject(),
                "confidence", JsonSchemas.number(),
                "reason", JsonSchemas.string())))));

        String answer = llmClient.completeJson(properties.models().extraction(), system, userPrompt(email, targets),
                objectMapper.valueToTree(schema));
        JsonNode items = parse(answer).map(json -> json.path("items")).orElse(null);
        if (items == null || !items.isArray()) {
            log.warn("Email {}: the model answered without an item list", email.id());
            return List.of();
        }

        List<ProposalDraft> drafts = new ArrayList<>();
        for (JsonNode item : items) {
            CollectionDto collection = bySlug.get(item.path("collection").asString(""));
            String title = item.path("title").asString("").trim();
            if (collection == null || title.isEmpty()) {
                continue;
            }
            Map<String, Object> values = values(collection, item.path("values"));
            if (values == null) {
                continue;
            }
            BigDecimal confidence = BigDecimal.valueOf(Math.clamp(item.path("confidence").asDouble(0.5), 0.0, 1.0))
                    .setScale(4, RoundingMode.HALF_UP);
            draft(collection, truncate(title, 300), values, confidence, truncate(item.path("reason").asString(""), 1000))
                    .ifPresent(drafts::add);
        }
        return drafts;
    }

    private String userPrompt(EmailContent email, List<CollectionDto> targets) {
        StringBuilder prompt = new StringBuilder("Collections:\n");
        for (CollectionDto collection : targets) {
            prompt.append("- ").append(collection.getSlug()).append(": ").append(collection.getName());
            if (collection.getDescription() != null) {
                prompt.append(" – ").append(collection.getDescription());
            }
            prompt.append('\n');
            for (CollectionFieldDto field : activeFields(collection)) {
                prompt.append("  - ").append(field.getSlug()).append(" (").append(field.getType());
                if (Boolean.TRUE.equals(field.getRequired())) {
                    prompt.append(", required");
                }
                allowedValues(field).ifPresent(values -> prompt.append(", one of ").append(String.join(" | ", values)));
                prompt.append(')');
                if (field.getDescription() != null) {
                    prompt.append(": ").append(field.getDescription());
                }
                prompt.append('\n');
            }
        }
        String received = email.receivedAt() != null
                ? email.receivedAt().atZone(properties.timezone()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                : "unknown";
        String body = Optional.ofNullable(email.body()).orElse("").strip();
        if (body.length() > MAX_BODY_CHARS) {
            body = body.substring(0, MAX_BODY_CHARS) + "\n[…truncated]";
        }
        prompt.append("\nEmail:\n")
                .append("From: ").append(Optional.ofNullable(email.fromName()).orElse("")).append(" <")
                .append(Optional.ofNullable(email.fromAddress()).orElse("")).append(">\n")
                .append("To: ").append(String.join(", ", Optional.ofNullable(email.to()).orElse(List.of()))).append('\n')
                .append("Date: ").append(received).append('\n')
                .append("Subject: ").append(Optional.ofNullable(email.subject()).orElse("")).append("\n\n")
                .append(body).append('\n');
        return prompt.toString();
    }

    /** Valid values of the item keyed by field slug, or {@code null} when a required value is missing or invalid. */
    private Map<String, Object> values(CollectionDto collection, JsonNode json) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (CollectionFieldDto dto : activeFields(collection)) {
            JsonNode node = json.path(dto.getSlug());
            boolean required = Boolean.TRUE.equals(dto.getRequired());
            if (node.isMissingNode() || node.isNull() || (node.isString() && node.asString().isBlank())) {
                if (required) return null;
                continue;
            }
            Object value = objectMapper.convertValue(node, Object.class);
            CollectionField field = CollectionField.builder()
                    .slug(new Slug(dto.getSlug()))
                    .type(FieldType.valueOf(dto.getType().name()))
                    .options(dto.getOptions() != null ? dto.getOptions() : Map.of())
                    .required(required)
                    .build();
            try {
                fieldTypeRegistry.handler(field.getType()).validate(field, value);
                values.put(dto.getSlug(), value);
            } catch (IllegalArgumentException | ClassCastException e) {
                if (required) return null;
            }
        }
        return values;
    }

    private Optional<ProposalDraft> draft(CollectionDto collection, String title, Map<String, Object> values,
                                          BigDecimal confidence, String reason) {
        Map<String, Object> keys = matchKeyValues(collection, values);
        Optional<RecordDto> existing = findExisting(collection, title, keys);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collectionId", collection.getId());
        if (existing.isPresent()) {
            RecordDto record = existing.get();
            Map<String, Object> missing = new LinkedHashMap<>();
            values.forEach((slug, value) -> {
                Object current = record.getValues() != null ? record.getValues().get(slug) : null;
                if (current == null || (current instanceof String text && text.isBlank())) {
                    missing.put(slug, value);
                }
            });
            if (missing.isEmpty()) {
                return Optional.empty();
            }
            payload.put("recordId", record.getId());
            payload.put("values", missing);
            return Optional.of(new ProposalDraft(ProposalKind.UPDATE_RECORD, payload, confidence, reason));
        }

        boolean pending = keys.isEmpty()
                ? proposalRepository.existsPendingCreateRecordWithTitle(collection.getId(), title)
                : keys.entrySet().stream().anyMatch(entry ->
                        proposalRepository.existsPendingCreateRecordWithValue(collection.getId(), entry.getKey(), String.valueOf(entry.getValue())));
        if (pending) {
            return Optional.empty();
        }
        payload.put("title", title);
        payload.put("values", values);
        return Optional.of(new ProposalDraft(ProposalKind.CREATE_RECORD, payload, confidence, reason));
    }

    /** Match-key values present in the item (all keys of the collection, or none). */
    private static Map<String, Object> matchKeyValues(CollectionDto collection, Map<String, Object> values) {
        List<String> matchKeys = Optional.ofNullable(collection.getMatchKeys()).orElse(List.of());
        Map<String, Object> keys = new LinkedHashMap<>();
        for (String key : matchKeys) {
            Object value = values.get(key);
            if (!(value instanceof String || value instanceof Number)) {
                return Map.of();
            }
            keys.put(key, value);
        }
        return keys;
    }

    private Optional<RecordDto> findExisting(CollectionDto collection, String title, Map<String, Object> keys) {
        List<String> filters = keys.isEmpty()
                ? List.of("title:eq:" + title)
                : keys.entrySet().stream().map(entry -> entry.getKey() + ":eq:" + entry.getValue()).toList();
        try {
            return recordService.listRecords(collection.getId(), filters, null, 0, 1).getItems().stream().findFirst();
        } catch (IllegalArgumentException e) {
            log.debug("Cannot look up existing records of collection {}: {}", collection.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private static List<CollectionFieldDto> activeFields(CollectionDto collection) {
        return Optional.ofNullable(collection.getFields()).orElse(List.of()).stream()
                .filter(field -> field.getArchivedAt() == null && !SKIPPED_TYPES.contains(field.getType()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Optional<List<String>> allowedValues(CollectionFieldDto field) {
        String key = switch (field.getType()) {
            case ENUM -> "values";
            case STATUS -> "states";
            default -> null;
        };
        if (key == null || field.getOptions() == null || !(field.getOptions().get(key) instanceof List<?> list)) {
            return Optional.empty();
        }
        return Optional.of(list.stream().map(String::valueOf).collect(Collectors.toList()));
    }

    private Optional<JsonNode> parse(String answer) {
        try {
            return Optional.ofNullable(objectMapper.readTree(answer));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }


    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
