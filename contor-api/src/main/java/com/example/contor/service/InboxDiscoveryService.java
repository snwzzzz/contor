package com.example.contor.service;

import com.example.contor.ai.DiscoveryEmail;
import com.example.contor.ai.JsonSchemas;
import com.example.contor.ai.LlmClient;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.DisplayConfigDto;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.Slug;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "What has happened so far": finds the kinds of things a user deals with in their inbox and turns them into
 * collection definitions. Map-reduce with the routing model: each batch of email headers yields candidate kinds,
 * one merge call turns all candidates into at most {@link #MAX_COLLECTIONS} collections. Everything the model returns
 * is validated here (slugs, field types, options), so suggestions are always creatable. No database access.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class InboxDiscoveryService {

    public static final String PROMPT_VERSION = "discover-v1";

    /** Emails sampled from the window; enough to see the recurring kinds without hours of discovery. */
    static final int MAX_SAMPLE = 400;
    static final int BATCH_SIZE = 40;
    static final int MAX_COLLECTIONS = 8;
    static final int MAX_FIELDS = 10;

    private static final List<String> FIELD_TYPES = List.of("TEXT", "LONG_TEXT", "NUMBER", "MONEY", "DATE", "DATETIME",
            "BOOLEAN", "ENUM", "STATUS", "URL", "EMAIL", "PHONE");
    private static final Set<String> MATCH_KEY_TYPES = Set.of("TEXT", "NUMBER", "URL", "EMAIL", "PHONE");

    private static final String MAP_SYSTEM = """
            You analyze a person's email inbox to find out what they keep track of. You see one batch of emails \
            (sender, subject, preview). Identify recurring KINDS of things, for example job applications, orders, \
            deliveries, invoices, subscriptions, contracts, insurance, bank matters, trips and bookings, university or \
            school matters, appointments with doctors or authorities, rentals, repairs.
            Do NOT list people, organizations, calendar events or to-dos: those are tracked already.
            Ignore newsletters and advertising unless they concern something the person bought, booked or signed up for.
            For each kind give a short English name, a one-sentence description, how many emails of this batch are \
            about it, and the facts worth recording as fields (name, type, description).""";

    private static final String REDUCE_SYSTEM = """
            You design collections (tables) for a personal management app from candidate kinds found in different \
            parts of one inbox. Merge candidates that mean the same thing. Keep kinds that clearly recur; drop vague, \
            one-off or advertising-only kinds and anything an existing collection already covers. Most important first.
            For each collection give:
            - slug: plural, lowercase snake_case, ASCII, 2-40 characters (e.g. job_applications)
            - name, description (one sentence), icon (a lucide icon name such as briefcase, package, receipt, plane)
            - emailCount: estimated number of emails about it; reason: one sentence why it is worth tracking
            - matchKey: slug of the field that identifies one record (an order or invoice number, a company); empty if none
            - 3 to 8 fields: slug (lowercase snake_case, never id, title, status, extra, created_at, last_modified or \
            created_by; every record already has a title), name, type, description, values
            Types: TEXT, LONG_TEXT, NUMBER, MONEY, DATE, DATETIME, BOOLEAN, ENUM, STATUS, URL, EMAIL, PHONE. ENUM needs \
            its allowed values, STATUS its ordered workflow states (e.g. applied, interview, offer, rejected); other \
            types have empty values.""";

    /** A creatable collection with the evidence behind it. */
    public record Suggestion(NewCollectionCommand command, int emailCount, String reason) {
    }

    private record Candidate(String name, String description, int emails, List<String> fields) {
    }

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public List<Suggestion> discover(List<DiscoveryEmail> emails, List<CollectionDto> existing) {
        List<DiscoveryEmail> sample = sample(emails);
        if (sample.isEmpty()) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int start = 0; start < sample.size(); start += BATCH_SIZE) {
            candidates.addAll(summarize(sample.subList(start, Math.min(sample.size(), start + BATCH_SIZE))));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        return merge(candidates, existing);
    }

    /** One email per sender and subject (numbers ignored), newest first, at most {@link #MAX_SAMPLE}. */
    private static List<DiscoveryEmail> sample(List<DiscoveryEmail> emails) {
        Map<String, DiscoveryEmail> distinct = new LinkedHashMap<>();
        for (DiscoveryEmail email : emails) {
            String subject = Optional.ofNullable(email.subject()).orElse("").toLowerCase(Locale.ROOT)
                    .replaceAll("^((re|fw|fwd|aw|wg)\\s*:\\s*)+", "").replaceAll("\\d+", "#").trim();
            String key = Optional.ofNullable(email.fromAddress()).orElse("").toLowerCase(Locale.ROOT) + "|" + subject;
            distinct.putIfAbsent(key, email);
            if (distinct.size() >= MAX_SAMPLE) {
                break;
            }
        }
        return List.copyOf(distinct.values());
    }

    private List<Candidate> summarize(List<DiscoveryEmail> batch) {
        String prompt = batch.stream()
                .map(email -> "- %s <%s> | %s | %s".formatted(
                        Optional.ofNullable(email.fromName()).orElse(""), Optional.ofNullable(email.fromAddress()).orElse(""),
                        Optional.ofNullable(email.subject()).orElse("(no subject)"), shorten(email.snippet(), 160)))
                .collect(Collectors.joining("\n", "Emails:\n", "\n"));
        Map<String, Object> schema = JsonSchemas.object(Map.of("types", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                "name", JsonSchemas.string(),
                "description", JsonSchemas.string(),
                "emails", JsonSchemas.integer(),
                "fields", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                        "name", JsonSchemas.string(),
                        "type", JsonSchemas.string(),
                        "description", JsonSchemas.string()))))))));

        Optional<JsonNode> answer = parse(llmClient.completeJson(properties.models().routing(), MAP_SYSTEM, prompt,
                objectMapper.valueToTree(schema)));
        List<Candidate> candidates = new ArrayList<>();
        answer.ifPresent(json -> json.path("types").forEach(type -> {
            String name = type.path("name").asString("").trim();
            if (name.isEmpty()) {
                return;
            }
            List<String> fields = new ArrayList<>();
            type.path("fields").forEach(field -> fields.add("%s (%s)".formatted(field.path("name").asString(""),
                    field.path("type").asString(""))));
            candidates.add(new Candidate(name, type.path("description").asString(""), type.path("emails").asInt(1), fields));
        }));
        return candidates;
    }

    private List<Suggestion> merge(List<Candidate> candidates, List<CollectionDto> existing) {
        String existingList = existing.stream()
                .map(collection -> collection.getSlug() + " (" + collection.getName() + ")")
                .collect(Collectors.joining(", "));
        String candidateList = candidates.stream()
                .map(candidate -> "- %s (%d emails): %s Fields: %s".formatted(candidate.name(), candidate.emails(),
                        candidate.description(), String.join(", ", candidate.fields())))
                .collect(Collectors.joining("\n"));
        String prompt = "Existing collections: " + existingList + "\n\nCandidates:\n" + candidateList + "\n";
        Map<String, Object> schema = JsonSchemas.object(Map.of("collections", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                "slug", JsonSchemas.string(),
                "name", JsonSchemas.string(),
                "description", JsonSchemas.string(),
                "icon", JsonSchemas.string(),
                "emailCount", JsonSchemas.integer(),
                "reason", JsonSchemas.string(),
                "matchKey", JsonSchemas.string(),
                "fields", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                        "slug", JsonSchemas.string(),
                        "name", JsonSchemas.string(),
                        "type", JsonSchemas.oneOf(FIELD_TYPES),
                        "description", JsonSchemas.string(),
                        "values", JsonSchemas.array(JsonSchemas.string())))))))));

        Optional<JsonNode> answer = parse(llmClient.completeJson(properties.models().routing(), REDUCE_SYSTEM, prompt,
                objectMapper.valueToTree(schema)));
        Set<String> taken = existing.stream().map(CollectionDto::getSlug).collect(Collectors.toCollection(HashSet::new));
        List<Suggestion> suggestions = new ArrayList<>();
        answer.ifPresent(json -> json.path("collections").forEach(collection ->
                toSuggestion(collection, taken).ifPresent(suggestion -> {
                    taken.add(suggestion.command().getSlug());
                    suggestions.add(suggestion);
                })));
        return suggestions.stream()
                .sorted(Comparator.comparingInt(Suggestion::emailCount).reversed())
                .limit(MAX_COLLECTIONS)
                .toList();
    }

    private Optional<Suggestion> toSuggestion(JsonNode json, Set<String> taken) {
        String slug = json.path("slug").asString("").trim().toLowerCase(Locale.ROOT);
        String name = json.path("name").asString("").trim();
        if (!isValidSlug(slug) || taken.contains(slug) || name.isEmpty()) {
            return Optional.empty();
        }

        List<NewCollectionFieldCommand> fields = new ArrayList<>();
        Set<String> fieldSlugs = new LinkedHashSet<>();
        for (JsonNode field : json.path("fields")) {
            if (fields.size() >= MAX_FIELDS) {
                break;
            }
            toField(field, fieldSlugs).ifPresent(command -> {
                fieldSlugs.add(command.getSlug());
                fields.add(command);
            });
        }

        DisplayConfigDto displayConfig = new DisplayConfigDto().defaultView("table");
        fields.stream().filter(field -> field.getType() == CollectionFieldType.STATUS).findFirst()
                .ifPresent(field -> displayConfig.kanbanStatusField(field.getSlug()));
        fields.stream().filter(field -> field.getType() == CollectionFieldType.DATE || field.getType() == CollectionFieldType.DATETIME)
                .findFirst().ifPresent(field -> displayConfig.calendarDateField(field.getSlug()));
        String matchKey = json.path("matchKey").asString("").trim();
        List<String> matchKeys = fields.stream()
                .filter(field -> field.getSlug().equals(matchKey) && MATCH_KEY_TYPES.contains(field.getType().name()))
                .map(NewCollectionFieldCommand::getSlug)
                .toList();

        NewCollectionCommand command = new NewCollectionCommand(slug, truncate(name, 200))
                .description(blankToNull(truncate(json.path("description").asString(""), 2000)))
                .icon(blankToNull(truncate(json.path("icon").asString(""), 100)))
                .matchKeys(new ArrayList<>(matchKeys))
                .displayConfig(displayConfig)
                .fields(fields);
        return Optional.of(new Suggestion(command, Math.max(0, json.path("emailCount").asInt(0)),
                json.path("reason").asString("")));
    }

    private static Optional<NewCollectionFieldCommand> toField(JsonNode json, Set<String> usedSlugs) {
        String slug = json.path("slug").asString("").trim().toLowerCase(Locale.ROOT);
        String type = json.path("type").asString("").trim().toUpperCase(Locale.ROOT);
        if (!isValidSlug(slug) || CollectionSchemaService.SYSTEM_COLUMNS.contains(slug) || usedSlugs.contains(slug)
                || !FIELD_TYPES.contains(type)) {
            return Optional.empty();
        }
        List<String> values = new ArrayList<>(new LinkedHashSet<>(json.path("values").valueStream()
                .map(value -> value.asString("").trim())
                .filter(value -> !value.isEmpty())
                .toList()));
        CollectionFieldType fieldType = CollectionFieldType.valueOf(type);
        Map<String, Object> options = new LinkedHashMap<>();
        if (fieldType == CollectionFieldType.ENUM || fieldType == CollectionFieldType.STATUS) {
            if (values.isEmpty()) {
                fieldType = CollectionFieldType.TEXT;
            } else {
                options.put(fieldType == CollectionFieldType.ENUM ? "values" : "states", values);
            }
        }
        String name = json.path("name").asString("").trim();
        return Optional.of(new NewCollectionFieldCommand(slug, truncate(name.isEmpty() ? slug : name, 200), fieldType)
                .description(blankToNull(truncate(json.path("description").asString(""), 2000)))
                .options(options));
    }

    private Optional<JsonNode> parse(String answer) {
        try {
            JsonNode json = objectMapper.readTree(answer);
            return json != null && json.isObject() ? Optional.of(json) : Optional.empty();
        } catch (JacksonException e) {
            log.warn("Discarding a malformed discovery answer: {}", e.getOriginalMessage());
            return Optional.empty();
        }
    }

    private static boolean isValidSlug(String value) {
        try {
            new Slug(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }


    private static String shorten(String text, int max) {
        String flat = Optional.ofNullable(text).orElse("").replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private static String truncate(String text, int max) {
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
