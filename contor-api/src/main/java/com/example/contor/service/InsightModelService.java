package com.example.contor.service;

import com.example.contor.ai.DigestApiItem;
import com.example.contor.ai.DigestEmail;
import com.example.contor.ai.Insight;
import com.example.contor.ai.JsonSchemas;
import com.example.contor.ai.LlmClient;
import com.example.contor.config.AiProperties;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The model pass of the insights (prompt {@link #PROMPT_VERSION}): one call over a compact digest of the user's recent
 * mail (one line per email) and their open to-dos and upcoming appointments, asking for what is only visible across
 * several of them. The answer is untrusted: an insight survives only with at least {@link #MIN_EVIDENCE} evidence email
 * that are in the digest (one is enough for "something is missing") a novelty of at least {@link #MIN_NOVELTY} and a confidence of at least {@link #MIN_CONFIDENCE}; follow-ups are limited to to-dos and
 * appointments with usable dates. Never logs mail text.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class InsightModelService {

    public static final String PROMPT_VERSION = "insight-v2";
    static final int MAX_INSIGHTS = 5;
    static final int MAX_FOLLOW_UPS = 2;
    /** One email can be enough: "something is missing" (a promised answer, a hotel) is one email and an absence. */
    static final int MIN_EVIDENCE = 1;
    static final double MIN_NOVELTY = 0.5;
    /** Measured: the useful insights came with 0.9+, a made-up one (a hotel for a newsletter) with 0.4. */
    static final double MIN_CONFIDENCE = 0.6;
    /** Characters of mail lines in the prompt (~5 000 tokens); the newest mail comes first and the rest is left out. */
    static final int DIGEST_BUDGET = 18_000;
    private static final int SNIPPET_CHARS = 160;
    /** Characters of app data lines in the prompt (~1 500 tokens), the latest change first. */
    static final int APP_BUDGET = 6_000;
    private static final String APP_TOKEN = "A";
    private static final String TASKS = "tasks";
    private static final String EVENTS = "events";

    private static final String SYSTEM = """
            You look at the last weeks of the user's email together with their open to-dos and upcoming appointments, and \
            point out what the user may have missed because it only shows across several emails. Answer with JSON only.
            Today is %s (%s).
            Go through these checks one by one:
            1. Recurring bills: compare the amounts of invoices or receipts from the same sender. A different amount for the \
            same service is a price change worth pointing out.
            2. Promised answers: a sender promised to reply or decide by a date (or "within 7 days") and nothing came since.
            3. Trips: for every flight, train or event in another city, look for an accommodation booking (hotel, apartment) \
            for the nights in between. None in the emails means it may still be missing.
            4. Questions: the user asked something and no answer arrived; someone wrote several times without an answer.
            5. Clashes: a deadline or appointment in an email that collides with another appointment.
            Rules:
            - Report every check that finds something. Only non-obvious observations: something that only shows when \
            emails are compared, or something that is missing. Never restate what a single email says.
            - Never repeat what a to-do or appointment already says.
            - State facts. No advice about the user's life, career or habits.
            - Write dates as YYYY-MM-DD and do not guess weekdays.
            - Never invent: evidence lists the ids (the numbers after #) of every email the observation is based on.
            - title: short. observation: what you noticed, with the facts. whyItMatters: one sentence.
            - novelty: 0 to 1, how unlikely it is that the user already noticed it. confidence: 0 to 1.
            - followUps: at most 2 concrete next steps, each a to-do (collection tasks, date = due date YYYY-MM-DD, optional) \
            or an appointment (collection events, date = ISO-8601 date-time with offset).
            - Return at most 5 insights, the most useful first; return an empty list when nothing is worth saying.""";

    /** Added only when the digest has app data, so the mail-only prompt stays the measured one. */
    private static final String APP_RULES = """

            App data: the lines starting with A are items of the user's apps (a job posting, an order, a shipment) that \
            appeared (new) or disappeared (gone) recently.
            6. Check whether an app item matters for an email or a to-do, e.g. a posting the user applied to is gone, or an \
            order shipped while a customer still asks where it is.
            - evidenceApiItems lists the numbers after A of every app item the observation is based on; an observation may \
            rest on emails, app items or both.""";

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Insights over {@code digest} (newest first); empty without mail or when the answer is unusable.
     *
     * @param openTasks    one line per open to-do
     * @param appointments one line per upcoming appointment
     */
    public List<Insight> findInsights(List<DigestEmail> digest, List<String> openTasks, List<String> appointments) {
        return findInsights(digest, List.of(), openTasks, appointments);
    }

    /**
     * Like {@link #findInsights(List, List, List)} with the recent items of the user's apps ({@code A<n>} tokens); an
     * insight may rest on emails, app items or both.
     */
    public List<Insight> findInsights(List<DigestEmail> digest, List<DigestApiItem> appData, List<String> openTasks,
                                      List<String> appointments) {
        if (digest.isEmpty() && appData.isEmpty()) {
            return List.of();
        }
        Set<Long> known = new LinkedHashSet<>();
        Set<Long> knownItems = new LinkedHashSet<>();
        String prompt = prompt(digest, appData, openTasks, appointments, known, knownItems);
        boolean withApps = !knownItems.isEmpty();
        LocalDate today = LocalDate.now(clock.withZone(properties.timezone()));
        String system = SYSTEM.formatted(today, today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)) + (withApps ? APP_RULES : "");
        Map<String, Object> followUp = JsonSchemas.object(JsonSchemas.properties(
                "collection", JsonSchemas.oneOf(List.of(TASKS, EVENTS)),
                "title", JsonSchemas.string(),
                "date", JsonSchemas.string()));
        List<Object> fields = new ArrayList<>(List.of(
                "title", JsonSchemas.string(),
                "observation", JsonSchemas.string(),
                "whyItMatters", JsonSchemas.string(),
                "evidence", JsonSchemas.array(JsonSchemas.integer())));
        if (withApps) fields.addAll(List.of("evidenceApiItems", JsonSchemas.array(JsonSchemas.integer())));
        fields.addAll(List.of(
                "confidence", JsonSchemas.number(),
                "novelty", JsonSchemas.number(),
                "followUps", JsonSchemas.array(followUp)));
        Map<String, Object> schema = JsonSchemas.object(Map.of("insights", JsonSchemas.array(JsonSchemas.object(
                JsonSchemas.properties(fields.toArray())))));

        String answer = llmClient.completeJson(properties.models().extraction(), system, prompt, objectMapper.valueToTree(schema));
        JsonNode json;
        try {
            json = objectMapper.readTree(answer);
        } catch (JacksonException e) {
            log.warn("The insight model answered with unreadable JSON");
            return List.of();
        }
        List<Insight> insights = new ArrayList<>();
        for (JsonNode item : json.path("insights")) {
            parse(item, known, knownItems).ifPresent(insights::add);
            if (insights.size() == MAX_INSIGHTS) {
                break;
            }
        }
        return insights;
    }

    private Optional<Insight> parse(JsonNode item, Set<Long> known, Set<Long> knownItems) {
        String title = item.path("title").asString("").strip();
        List<Long> evidence = new ArrayList<>(new LinkedHashSet<>(numbers(item.path("evidence"))));
        List<Long> items = new ArrayList<>(new LinkedHashSet<>(numbers(item.path("evidenceApiItems"))));
        // Anything cited that was not in the prompt is invented: the whole insight goes.
        if (title.isEmpty() || evidence.size() + items.size() < MIN_EVIDENCE || !known.containsAll(evidence) || !knownItems.containsAll(items)
                || item.path("novelty").asDouble(0) < MIN_NOVELTY || item.path("confidence").asDouble(1) < MIN_CONFIDENCE) {
            return Optional.empty();
        }
        List<Insight.FollowUp> followUps = new ArrayList<>();
        for (JsonNode followUp : item.path("followUps")) {
            followUp(followUp).ifPresent(followUps::add);
            if (followUps.size() == MAX_FOLLOW_UPS) {
                break;
            }
        }
        String key = "model:" + evidence.stream().sorted().map(String::valueOf).collect(Collectors.joining(","))
                + (items.isEmpty() ? "" : "|" + APP_TOKEN + items.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        BigDecimal confidence = BigDecimal.valueOf(Math.clamp(item.path("confidence").asDouble(0.5), 0.0, 1.0)).setScale(4, RoundingMode.HALF_UP);
        return Optional.of(new Insight(key, title, item.path("observation").asString("").strip(), item.path("whyItMatters").asString("").strip(),
                evidence, items, followUps, "model", confidence));
    }

    /** A to-do (date optional, YYYY-MM-DD) or an appointment (ISO date-time required); anything else is dropped. */
    private static Optional<Insight.FollowUp> followUp(JsonNode node) {
        String collection = node.path("collection").asString("");
        String title = node.path("title").asString("").strip();
        String date = node.path("date").asString("").strip();
        if (title.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (TASKS.equals(collection)) {
                return Optional.of(new Insight.FollowUp(TASKS, title, date.isEmpty() ? Map.of() : Map.of("due_date", LocalDate.parse(date).toString())));
            }
            if (EVENTS.equals(collection)) {
                return Optional.of(new Insight.FollowUp(EVENTS, title, Map.of("starts_at", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.parse(date)))));
            }
        } catch (DateTimeParseException e) {
            return TASKS.equals(collection) ? Optional.of(new Insight.FollowUp(TASKS, title, Map.of())) : Optional.empty();
        }
        return Optional.empty();
    }

    private static List<Long> numbers(JsonNode array) {
        List<Long> numbers = new ArrayList<>();
        for (JsonNode node : array) {
            if (node.isNumber()) {
                numbers.add(node.asLong());
            }
        }
        return numbers;
    }

    /**
     * The digest lines within {@link #DIGEST_BUDGET} and the app data within {@link #APP_BUDGET}; {@code known} and
     * {@code knownItems} receive the ids that made it into the prompt.
     */
    private String prompt(List<DigestEmail> digest, List<DigestApiItem> appData, List<String> openTasks, List<String> appointments,
                          Set<Long> known, Set<Long> knownItems) {
        StringBuilder prompt = new StringBuilder("Emails, newest first:\n");
        int used = 0;
        for (DigestEmail email : digest) {
            String line = line(email);
            if (used + line.length() > DIGEST_BUDGET) {
                prompt.append("[").append(digest.size() - known.size()).append(" older emails left out]\n");
                break;
            }
            prompt.append(line);
            used += line.length();
            known.add(email.id());
        }
        if (!appData.isEmpty()) {
            prompt.append("\nData from your apps, newest first:\n");
            int usedApp = 0;
            for (DigestApiItem item : appData) {
                String line = line(item);
                if (usedApp + line.length() > APP_BUDGET) {
                    prompt.append("[").append(appData.size() - knownItems.size()).append(" older items left out]\n");
                    break;
                }
                prompt.append(line);
                usedApp += line.length();
                knownItems.add(item.id());
            }
        }
        if (!openTasks.isEmpty()) {
            prompt.append("\nOpen to-dos:\n");
            openTasks.forEach(task -> prompt.append("- ").append(task).append('\n'));
        }
        if (!appointments.isEmpty()) {
            prompt.append("\nUpcoming appointments:\n");
            appointments.forEach(appointment -> prompt.append("- ").append(appointment).append('\n'));
        }
        return prompt.toString();
    }

    private String line(DigestApiItem item) {
        String data = Optional.ofNullable(item.data()).orElse("").replaceAll("\\s+", " ").strip();
        if (data.length() > SNIPPET_CHARS) {
            data = data.substring(0, SNIPPET_CHARS) + "…";
        }
        String day = item.at() != null ? item.at().atZone(properties.timezone()).toLocalDate().toString() : "?";
        return APP_TOKEN + item.id() + " " + day + " · " + item.app() + " · " + item.endpoint() + " · " + (item.gone() ? "gone" : "new")
                + ": " + Optional.ofNullable(item.label()).orElse("") + " " + data + "\n";
    }

    private String line(DigestEmail email) {
        boolean own = "sent".equals(email.folder());
        String who = own ? "you →" : Optional.ofNullable(email.fromName()).filter(name -> !name.isBlank())
                .map(name -> name + " <" + Optional.ofNullable(email.fromAddress()).orElse("") + ">")
                .orElse(Optional.ofNullable(email.fromAddress()).orElse("unknown"));
        String snippet = Optional.ofNullable(email.snippet()).orElse("").replaceAll("\\s+", " ").strip();
        if (snippet.length() > SNIPPET_CHARS) {
            snippet = snippet.substring(0, SNIPPET_CHARS) + "…";
        }
        String day = email.receivedAt() != null ? email.receivedAt().atZone(properties.timezone()).toLocalDate().toString() : "?";
        return "#" + email.id() + " " + day + " · " + who + " · " + Optional.ofNullable(email.subject()).orElse("") + ": " + snippet + "\n";
    }
}
