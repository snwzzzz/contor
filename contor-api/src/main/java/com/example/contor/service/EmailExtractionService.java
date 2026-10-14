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
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.RelationTypeDto;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    public static final String PROMPT_VERSION = "extract-v7";

    static final int MAX_BODY_CHARS = 8000;
    private static final String TASKS = "tasks";

    /** The AI never sets references or files. */
    private static final Set<CollectionFieldType> SKIPPED_TYPES = Set.of(CollectionFieldType.RELATION, CollectionFieldType.FILE);

    /** Second, short call: which of the extracted items the email says are related. Kept apart from the extraction because
     *  listing relation types there makes the model invent records (organizations, events) just to relate them. */
    private static final String RELATIONS_SYSTEM = """
            You find the relations between records extracted from one email of the user. Answer with JSON only.
            Return a relation only when the email clearly states how two of the listed items are related (for example a \
            person's employer in the signature, or who attends a meeting). Use the refs of the items and one of the \
            listed relation types, from the item that the type's first collection describes. List each relation once. \
            reason: at most 12 words. When unsure, return an empty list.""";

    /**
     * Without an owner profile: the personal rules measured with the personal corpus (extract-v6). With a profile the
     * business rules below apply (extract-v7: whose side an email is on, shipments and deadlines, updates of records).
     */
    private static final String SYSTEM_PERSONAL = """
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
            - Resolve relative dates ("Friday", "tomorrow", "next week", "by Thursday") relative to the date the email \
            was sent (its Date line, with weekday), never relative to today.
            - DATE as YYYY-MM-DD; DATETIME as ISO-8601 with offset in the user's time zone unless the email names \
            another zone; MONEY as {"amount": 12.5, "currency": "EUR"}; ENUM and STATUS only with the listed values.
            - confidence between 0 and 1; reason: one short sentence quoting the evidence.
            - Advertising, newsletters and notifications without anything to record: return no items.""";

    private static final String SYSTEM_BUSINESS = """
            You are the assistant inside contor, an app that keeps the user's records, calendar and to-dos from their \
            mail. You read one email of the user and extract what is worth recording into the user's collections. Answer \
            with JSON only.
            Today is %s (%s). The user's time zone is %s. The user's own address is %s: never record the user.%s
            Rules:
            - Record only facts stated in the email or in the earlier emails shown with it. Leave a field out instead of \
            guessing; never invent numbers (order, invoice, tracking numbers, amounts).
            - Decide from the user's point of view whose order, invoice or shipment it is; the collection descriptions say \
            which collection fits.
            - people: individual humans the user deals with (a personal sender, people named with contact details). \
            Never companies, newsletters, no-reply or system senders. A human sender is always a people item (email from \
            the From line, phone and role from the signature), colleagues too.
            - organizations: companies, shops, employers, authorities, schools the user actually deals with; the company \
            of a human sender (domain from the address) is one, except the user's own company.
            - events: appointments with people, meetings, calls, visits, interviews, trade fairs, bookings, trips and \
            slots at a fixed time. starts_at is required. Delivery dates, ETAs, dispatch and pickup dates are not events: \
            they belong to the order or shipment they concern.
            - tasks: concrete things the user has to do (reply, pay, sign, send, call, order, change something), with \
            due_date if stated. A deadline to do something is a task with that due_date, not an event. Title in the \
            language of the email, starting with the action.
            - A notification about a shipment or delivery with a tracking or consignment number is a record of that \
            shipment, not a notification without anything to record. When it names an order (purchase or sales order \
            number), it also returns that order with its new state (shipped, received, delivered).
            - An email that confirms, changes, cancels or reports the arrival of something already recorded (see "Already \
            recorded" with the earlier emails) returns that record again: same collection, its key value (order number, \
            tracking number, ...) and the values that changed, e.g. the new state or date.
            - any other collection: only when the email is clearly about such a thing.
            - title: short and specific, e.g. a person's full name, "Interview at Acme", "Pay invoice 2024-118".
            - Resolve relative dates ("Friday", "tomorrow", "next week", "by Thursday") relative to the date the email \
            was sent (its Date line, with weekday), never relative to today.
            - DATE as YYYY-MM-DD; DATETIME as ISO-8601 with offset in the user's time zone unless the email names \
            another zone; MONEY as {"amount": 12.5, "currency": "EUR"}; ENUM and STATUS only with the listed values.
            - confidence between 0 and 1; reason: one short sentence quoting the evidence.
            - Return no items only for advertising, newsletters and automated notifications with nothing to record. An \
            email a person writes to the user (customer, supplier, colleague) always yields at least its sender.""";

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final FieldTypeRegistry fieldTypeRegistry;
    private final RecordService recordService;
    private final ProposalRepository proposalRepository;
    private final RelationTypeService relationTypeService;
    private final RelationService relationService;
    private final Clock clock;

    @Override
    public List<ProposalDraft> analyze(EmailContent email, List<CollectionDto> targets, String ownerProfile) {
        if (targets.isEmpty()) {
            return List.of();
        }
        Map<String, CollectionDto> bySlug = new LinkedHashMap<>();
        targets.forEach(collection -> bySlug.put(collection.getSlug(), collection));

        LocalDate today = LocalDate.now(clock.withZone(properties.timezone()));
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String system = ownerProfile == null || ownerProfile.isBlank()
                ? SYSTEM_PERSONAL.formatted(today, weekday, properties.timezone(), email.mailbox())
                : SYSTEM_BUSINESS.formatted(today, weekday, properties.timezone(), email.mailbox(),
                "\nAbout the user, in their own words (it describes the user, never a record): " + ownerProfile.strip());
        Map<String, Object> schema = JsonSchemas.object(Map.of("items", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                "collection", JsonSchemas.oneOf(List.copyOf(bySlug.keySet())),
                "title", JsonSchemas.string(),
                "values", JsonSchemas.freeObject(),
                "confidence", JsonSchemas.number(),
                "reason", JsonSchemas.string())))));

        String answer = llmClient.completeJson(properties.models().extraction(), system, userPrompt(email, targets),
                objectMapper.valueToTree(schema));
        JsonNode json = parse(answer).orElse(null);
        JsonNode items = json != null ? json.path("items") : null;
        if (items == null || !items.isArray()) {
            log.warn("Email {}: the model answered without an item list", email.id());
            return List.of();
        }

        List<ProposalDraft> drafts = new ArrayList<>();
        Map<String, Entity> entities = new LinkedHashMap<>();
        for (JsonNode item : items) {
            CollectionDto collection = bySlug.get(item.path("collection").asString(""));
            String title = item.path("title").asString("").trim();
            if (collection == null || title.isEmpty()) {
                continue;
            }
            Map<String, Object> values = values(collection, item.path("values"));
            if (values == null || isOutdatedToDo(collection, values, today)) {
                continue;
            }
            BigDecimal confidence = confidence(item);
            // Refs are ours (r1, r2, …), not the model's: asking for them in the extraction call changes what it extracts.
            String ref = "r" + (entities.size() + 1);
            Drafted drafted = draft(collection, truncate(title, 300), values, confidence, truncate(item.path("reason").asString(""), 1000), ref);
            drafted.draft().ifPresent(drafts::add);
            if (drafted.endpoint() != null) {
                entities.put(ref, new Entity(collection.getId(), collection.getSlug(), truncate(title, 300), drafted.endpoint()));
            }
        }
        drafts.addAll(relationDrafts(email, bySlug, entities));
        return drafts;
    }

    private static BigDecimal confidence(JsonNode node) {
        return BigDecimal.valueOf(Math.clamp(node.path("confidence").asDouble(0.5), 0.0, 1.0)).setScale(4, RoundingMode.HALF_UP);
    }

    /** Where a ref points: a record that exists, a record the answer creates (draft), or one a pending proposal creates. */
    private sealed interface Endpoint permits Existing, FromDraft, FromProposal {
    }

    private record Existing(Long collectionId, Long recordId) implements Endpoint {
    }

    private record FromDraft(String ref) implements Endpoint {
    }

    private record FromProposal(Long proposalId) implements Endpoint {
    }

    private record Entity(Long collectionId, String collectionSlug, String title, Endpoint endpoint) {
    }

    private record Drafted(Optional<ProposalDraft> draft, Endpoint endpoint) {
    }

    /** Relation types the model may use: both restricting collections (if any) are among the targets. */
    private Map<String, RelationTypeDto> usableRelationTypes(Map<String, CollectionDto> targets) {
        Set<Long> ids = targets.values().stream().map(CollectionDto::getId).collect(Collectors.toSet());
        Map<String, RelationTypeDto> usable = new LinkedHashMap<>();
        for (RelationTypeDto type : relationTypeService.findAll()) {
            if ((type.getFromCollectionId() == null || ids.contains(type.getFromCollectionId()))
                    && (type.getToCollectionId() == null || ids.contains(type.getToCollectionId()))) {
                usable.put(type.getSlug(), type);
            }
        }
        return usable;
    }

    /** Asks which of the items are related (only with 2+ items and usable relation types) and turns the answer into drafts. */
    private List<ProposalDraft> relationDrafts(EmailContent email, Map<String, CollectionDto> targets, Map<String, Entity> entities) {
        List<ProposalDraft> drafts = new ArrayList<>();
        Map<String, RelationTypeDto> types = usableRelationTypes(targets);
        if (types.isEmpty() || entities.size() < 2) {
            return drafts;
        }
        Map<Long, String> slugs = new HashMap<>();
        targets.values().forEach(collection -> slugs.put(collection.getId(), collection.getSlug()));
        StringBuilder prompt = new StringBuilder("Relation types:\n");
        types.values().forEach(type -> prompt.append("- ").append(type.getSlug()).append(": ")
                .append(slugs.getOrDefault(type.getFromCollectionId(), "any")).append(" → ")
                .append(slugs.getOrDefault(type.getToCollectionId(), "any")).append(" (")
                .append(type.getName()).append(type.getInverseName() != null ? " / " + type.getInverseName() : "")
                .append(Boolean.TRUE.equals(type.getDirected()) ? ", directed" : ", either direction").append(")\n"));
        prompt.append("\nItems:\n");
        entities.forEach((ref, entity) -> prompt.append("- ").append(ref).append(": ").append(entity.collectionSlug())
                .append(" – ").append(entity.title()).append('\n'));
        prompt.append('\n').append(emailText(email));
        Map<String, Object> schema = JsonSchemas.object(Map.of("relations", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                "type", JsonSchemas.oneOf(List.copyOf(types.keySet())),
                "from", JsonSchemas.string(),
                "to", JsonSchemas.string(),
                "confidence", JsonSchemas.number(),
                "reason", JsonSchemas.string())))));

        JsonNode relations = parse(llmClient.completeJson(properties.models().extraction(), RELATIONS_SYSTEM, prompt.toString(),
                objectMapper.valueToTree(schema))).map(json -> json.path("relations")).orElse(null);
        if (relations == null || !relations.isArray()) {
            return drafts;
        }
        Set<List<Object>> seen = new HashSet<>();
        for (JsonNode relation : relations) {
            RelationTypeDto type = types.get(relation.path("type").asString(""));
            Entity from = entities.get(relation.path("from").asString(""));
            Entity to = entities.get(relation.path("to").asString(""));
            if (type == null || from == null || to == null || from.endpoint().equals(to.endpoint())) {
                continue;
            }
            if (!fits(type, from.collectionId(), to.collectionId())) {
                if (Boolean.TRUE.equals(type.getDirected()) || !fits(type, to.collectionId(), from.collectionId())) {
                    continue;
                }
                Entity swapped = from;
                from = to;
                to = swapped;
            }
            if (!seen.add(List.of(type.getId(), from.endpoint(), to.endpoint()))
                    || (!Boolean.TRUE.equals(type.getDirected()) && !seen.add(List.of(type.getId(), to.endpoint(), from.endpoint())))
                    || alreadyRelated(type, from, to)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("relationTypeId", type.getId());
            payload.put("from", payload(from.endpoint()));
            payload.put("to", payload(to.endpoint()));
            List<String> dependsOnRefs = new ArrayList<>();
            List<Long> dependsOnProposalIds = new ArrayList<>();
            for (Endpoint endpoint : List.of(from.endpoint(), to.endpoint())) {
                if (endpoint instanceof FromDraft draft) {
                    dependsOnRefs.add(draft.ref());
                } else if (endpoint instanceof FromProposal proposal) {
                    dependsOnProposalIds.add(proposal.proposalId());
                }
            }
            drafts.add(new ProposalDraft(ProposalKind.CREATE_RELATION, payload, confidence(relation),
                    truncate(relation.path("reason").asString(""), 1000), null, dependsOnRefs, dependsOnProposalIds));
        }
        return drafts;
    }

    private static boolean fits(RelationTypeDto type, Long fromCollectionId, Long toCollectionId) {
        return (type.getFromCollectionId() == null || type.getFromCollectionId().equals(fromCollectionId))
                && (type.getToCollectionId() == null || type.getToCollectionId().equals(toCollectionId));
    }

    private static Map<String, Object> payload(Endpoint endpoint) {
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (endpoint) {
            case Existing existing -> {
                payload.put("collectionId", existing.collectionId());
                payload.put("recordId", existing.recordId());
            }
            case FromDraft draft -> payload.put("ref", draft.ref());
            case FromProposal proposal -> payload.put("proposalId", proposal.proposalId());
        }
        return payload;
    }

    /** Only two existing records can already be related. */
    private boolean alreadyRelated(RelationTypeDto type, Entity from, Entity to) {
        if (!(from.endpoint() instanceof Existing a) || !(to.endpoint() instanceof Existing b)) {
            return false;
        }
        boolean directed = Boolean.TRUE.equals(type.getDirected());
        for (RelationDto relation : relationService.listRelations(a.collectionId(), a.recordId())) {
            if (!type.getId().equals(relation.getRelationTypeId())) {
                continue;
            }
            boolean forward = a.collectionId().equals(relation.getFromCollectionId()) && a.recordId().equals(relation.getFromRecordId())
                    && b.collectionId().equals(relation.getToCollectionId()) && b.recordId().equals(relation.getToRecordId());
            boolean backward = !directed && b.collectionId().equals(relation.getFromCollectionId()) && b.recordId().equals(relation.getFromRecordId())
                    && a.collectionId().equals(relation.getToCollectionId()) && a.recordId().equals(relation.getToRecordId());
            if (forward || backward) {
                return true;
            }
        }
        return false;
    }

    /**
     * A to-do whose deadline lies before today is history (typically from older mail in the first analysis), not
     * something to do: it is dropped. Past calendar events are kept as history.
     */
    private static boolean isOutdatedToDo(CollectionDto collection, Map<String, Object> values, LocalDate today) {
        if (!TASKS.equals(collection.getSlug()) || !(values.get("due_date") instanceof String due)) {
            return false;
        }
        try {
            return LocalDate.parse(due).isBefore(today);
        } catch (DateTimeParseException e) {
            return false;
        }
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
        prompt.append('\n');
        if (email.hasContext()) {
            prompt.append(email.context().strip()).append("\n\n");
        }
        return prompt.append(emailText(email)).toString();
    }

    /** The email as the model reads it: sender, recipients, date with weekday, subject, body (truncated). */
    private String emailText(EmailContent email) {
        String received = email.receivedAt() != null
                ? email.receivedAt().atZone(properties.timezone()).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + ", " + email.receivedAt().atZone(properties.timezone()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                : "unknown";
        String body = Optional.ofNullable(email.body()).orElse("").strip();
        if (body.length() > MAX_BODY_CHARS) {
            body = body.substring(0, MAX_BODY_CHARS) + "\n[…truncated]";
        }
        return new StringBuilder("Email:\n")
                .append("From: ").append(Optional.ofNullable(email.fromName()).orElse("")).append(" <")
                .append(Optional.ofNullable(email.fromAddress()).orElse("")).append(">\n")
                .append("To: ").append(String.join(", ", Optional.ofNullable(email.to()).orElse(List.of()))).append('\n')
                .append("Date: ").append(received).append('\n')
                .append("Subject: ").append(Optional.ofNullable(email.subject()).orElse("")).append("\n\n")
                .append(body).append('\n')
                .toString();
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

    private Drafted draft(CollectionDto collection, String title, Map<String, Object> values, BigDecimal confidence, String reason,
                          String ref) {
        Map<String, Object> keys = matchKeyValues(collection, values);
        Optional<RecordDto> existing = findExisting(collection, title, keys);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collectionId", collection.getId());
        if (existing.isPresent()) {
            RecordDto record = existing.get();
            Endpoint endpoint = new Existing(collection.getId(), record.getId());
            Map<String, Object> changes = changes(collection, record, values);
            if (changes.isEmpty()) {
                return new Drafted(Optional.empty(), endpoint);
            }
            payload.put("recordId", record.getId());
            payload.put("values", changes);
            return new Drafted(Optional.of(new ProposalDraft(ProposalKind.UPDATE_RECORD, payload, confidence, reason)), endpoint);
        }

        Optional<Long> pending = keys.isEmpty()
                ? proposalRepository.findPendingCreateRecordIdWithTitle(collection.getId(), title)
                : keys.entrySet().stream()
                        .map(entry -> proposalRepository.findPendingCreateRecordIdWithValue(collection.getId(), entry.getKey(), String.valueOf(entry.getValue())))
                        .flatMap(Optional::stream).findFirst();
        if (pending.isPresent()) {
            return new Drafted(Optional.empty(), new FromProposal(pending.get()));
        }
        payload.put("title", title);
        payload.put("values", values);
        return new Drafted(Optional.of(new ProposalDraft(ProposalKind.CREATE_RECORD, payload, confidence, reason, ref, List.of(), List.of())),
                ref != null ? new FromDraft(ref) : null);
    }

    /**
     * What an email may change on an existing record: empty values get filled; a STATUS only moves forward along the
     * field's states (booked, ..., delivered; a quoted older mail must not move it back); dates and amounts take the
     * newer mail's value. Other existing values (names, texts) stay as they are.
     */
    private static Map<String, Object> changes(CollectionDto collection, RecordDto record, Map<String, Object> values) {
        Map<String, Object> changes = new LinkedHashMap<>();
        Map<String, CollectionFieldDto> fields = new HashMap<>();
        activeFields(collection).forEach(field -> fields.put(field.getSlug(), field));
        values.forEach((slug, value) -> {
            Object current = record.getValues() != null ? record.getValues().get(slug) : null;
            if (current == null || (current instanceof String text && text.isBlank())) {
                changes.put(slug, value);
                return;
            }
            CollectionFieldDto field = fields.get(slug);
            if (field == null) {
                return;
            }
            boolean changed = switch (field.getType()) {
                case STATUS -> movesForward(field, current, value);
                case DATE, DATETIME, MONEY -> !sameValue(field.getType(), current, value);
                default -> false;
            };
            if (changed) {
                changes.put(slug, value);
            }
        });
        return changes;
    }

    private static boolean movesForward(CollectionFieldDto field, Object current, Object value) {
        List<String> states = allowedValues(field).orElse(List.of());
        int from = states.indexOf(String.valueOf(current));
        int to = states.indexOf(String.valueOf(value));
        return to >= 0 && to > from;
    }

    private static boolean sameValue(CollectionFieldType type, Object current, Object value) {
        try {
            return switch (type) {
                case DATE -> LocalDate.parse(String.valueOf(current).substring(0, 10)).equals(LocalDate.parse(String.valueOf(value).substring(0, 10)));
                case DATETIME -> OffsetDateTime.parse(String.valueOf(current)).toInstant().equals(OffsetDateTime.parse(String.valueOf(value)).toInstant());
                case MONEY -> current instanceof Map<?, ?> a && value instanceof Map<?, ?> b
                        && new BigDecimal(String.valueOf(a.get("amount"))).compareTo(new BigDecimal(String.valueOf(b.get("amount")))) == 0
                        && String.valueOf(a.get("currency")).equalsIgnoreCase(String.valueOf(b.get("currency")));
                default -> String.valueOf(current).equals(String.valueOf(value));
            };
        } catch (RuntimeException e) {
            return String.valueOf(current).equals(String.valueOf(value));
        }
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
            return recordService.listRecords(collection.getId(), filters, null, null, 0, 1).getItems().stream().findFirst();
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
