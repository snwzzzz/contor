package com.example.contor.ai;

import com.example.contor.domain.FindingSeverity;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A report as the model drafted it. The answer is free text ({@code answer}, split into sentences, sources in square
 * brackets after a sentence); the other parts are lists of sentences with their source codes. Parsing is lenient about
 * shape: blank sentences are dropped, a finding with quotes but no sentence keeps its title as its sentence, severities
 * the model may not set (CONFLICT) are neutralized, and every list is capped. A {@code summary} list is still read when
 * there is no {@code answer}.
 */
public record ReportDraft(String title, List<Claim> summary, List<Finding> findings, List<Claim> openQuestions,
                          List<Claim> nextSteps, List<Section> customSections) {

    public static final int MAX_SUMMARY = 6;
    public static final int MAX_FINDINGS = 8;
    public static final int MAX_FINDING_CLAIMS = 4;
    public static final int MAX_QUOTES = 4;
    public static final int MAX_LIST = 6;
    public static final int MAX_CUSTOM_SECTIONS = 3;

    /** A source token, also with its record or email in front ({@code R1:F5}, {@code E3:P3.1}). */
    private static final String TOKEN = "(?:[RE]\\d+:)?[RFLPE]\\d+(?:\\.\\d+)?";
    /** A bracket that holds nothing but source tokens, ranges and separators. */
    private static final Pattern INLINE_REFS = Pattern.compile("(?i)\\s*[(\\[]\\s*(" + TOKEN + "(?:\\s*-\\s*[RFLPE]?\\d+)?"
            + "(?:\\s*(?:[,;/]|\\band\\b|\\bund\\b)\\s*" + TOKEN + "(?:\\s*-\\s*[RFLPE]?\\d+)?)*)\\s*[)\\]]");
    private static final Pattern RANGE = Pattern.compile("([RFLPE])(\\d+)(?:\\s*-\\s*[RFLPE]?(\\d+))?");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+([.,;:!?])");
    private static final Pattern PARAGRAPHS = Pattern.compile("\\n\\s*\\n");
    /** A sentence ends after a letter, a bracket or a quote mark and . ! ? — not after a number ("23.07. Die …"). */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[\\p{L})\\]\"”'][.!?])\\s+(?=[\\p{Lu}\\p{N}„\"“])");

    /** Whether the model wrote no sentence at all. */
    public boolean isEmpty() {
        return summary.isEmpty() && findings.isEmpty() && openQuestions.isEmpty() && nextSteps.isEmpty() && customSections.isEmpty();
    }

    /** One sentence and the source tokens it cites. */
    public record Claim(String text, List<String> refs) {}

    public record Finding(String title, FindingSeverity severity, List<Claim> claims, List<References.Quote> quotes) {}

    public record Section(String title, List<Claim> claims) {}

    public static ReportDraft parse(JsonNode json) {
        List<Finding> findings = new ArrayList<>();
        for (JsonNode node : json.path("findings")) {
            String title = node.path("title").asString("").strip();
            List<Claim> claims = claims(node.path("claims"), MAX_FINDING_CLAIMS);
            List<References.Quote> quotes = new ArrayList<>();
            for (JsonNode quote : node.path("quotes")) {
                String text = quote.path("text").asString("").strip();
                if (!text.isEmpty() && quotes.size() < MAX_QUOTES) {
                    quotes.add(new References.Quote(quote.path("ref").asString(""), text));
                }
            }
            if (claims.isEmpty() && !quotes.isEmpty() && !title.isEmpty()) {
                claims = List.of(new Claim(title.matches(".*[.!?]$") ? title : title + ".", quotes.stream().map(References.Quote::token).toList()));
            }
            if (title.isEmpty() || claims.isEmpty()) {
                continue;
            }
            findings.add(new Finding(title, severity(node.path("severity").asString("")), claims, quotes));
            if (findings.size() == MAX_FINDINGS) {
                break;
            }
        }
        List<Section> sections = new ArrayList<>();
        for (JsonNode node : json.path("customSections")) {
            String title = node.path("title").asString("").strip();
            List<Claim> claims = claims(node.path("claims"), MAX_LIST);
            if (!title.isEmpty() && !claims.isEmpty() && sections.size() < MAX_CUSTOM_SECTIONS) {
                sections.add(new Section(title, claims));
            }
        }
        String answer = json.path("answer").asString("").strip();
        List<Claim> summary = answer.isEmpty() ? claims(json.path("summary"), MAX_SUMMARY) : sentences(answer);
        return new ReportDraft(json.path("title").asString("").strip(), summary, findings,
                claims(json.path("openQuestions"), MAX_LIST), claims(json.path("nextSteps"), MAX_LIST), sections);
    }

    /** The answer text as sentences, each with the codes in the brackets after it. */
    private static List<Claim> sentences(String answer) {
        List<Claim> sentences = new ArrayList<>();
        for (String paragraph : PARAGRAPHS.split(answer)) {
            for (String sentence : SENTENCE_END.split(paragraph.replace('\n', ' ').strip())) {
                Claim claim = claim(sentence, new ArrayList<>());
                if (claim != null) {
                    sentences.add(claim);
                }
            }
        }
        return sentences;
    }

    /** The JSON schema the model answers with (all keys required; empty lists are allowed). */
    public static Map<String, Object> schema() {
        Map<String, Object> claim = JsonSchemas.object(JsonSchemas.properties(
                "text", JsonSchemas.string(),
                "refs", JsonSchemas.array(JsonSchemas.string())));
        Map<String, Object> quote = JsonSchemas.object(JsonSchemas.properties(
                "ref", JsonSchemas.string(),
                "text", JsonSchemas.string()));
        Map<String, Object> finding = JsonSchemas.object(JsonSchemas.properties(
                "title", JsonSchemas.string(),
                "severity", JsonSchemas.oneOf(List.of("POSITIVE", "NEUTRAL", "PROBLEM", "RISK")),
                "claims", JsonSchemas.array(claim),
                "quotes", JsonSchemas.array(quote)));
        Map<String, Object> section = JsonSchemas.object(JsonSchemas.properties(
                "title", JsonSchemas.string(),
                "claims", JsonSchemas.array(claim)));
        return JsonSchemas.object(JsonSchemas.properties(
                "answer", JsonSchemas.string(),
                "title", JsonSchemas.string(),
                "findings", JsonSchemas.array(finding),
                "openQuestions", JsonSchemas.array(claim),
                "nextSteps", JsonSchemas.array(claim),
                "customSections", JsonSchemas.array(section)));
    }

    private static List<Claim> claims(JsonNode array, int max) {
        List<Claim> claims = new ArrayList<>();
        for (JsonNode node : array) {
            List<String> refs = new ArrayList<>();
            for (JsonNode ref : node.path("refs")) {
                if (ref.isString()) {
                    refs.add(ref.asString().strip().replace("[", "").replace("]", ""));
                }
            }
            Claim claim = claim(node.path("text").asString(""), refs);
            if (claim == null) {
                continue;
            }
            claims.add(claim);
            if (claims.size() == max) {
                break;
            }
        }
        return claims;
    }

    /** A sentence without the codes written into it ("(R1, F1-F3)", "[E3; P4.2]"), which join {@code refs}; null when blank. */
    private static Claim claim(String raw, List<String> refs) {
        String text = raw.strip();
        if (text.isEmpty()) {
            return null;
        }
        Matcher inline = INLINE_REFS.matcher(text);
        StringBuilder cleaned = new StringBuilder();
        while (inline.find()) {
            for (String ref : expandRanges(inline.group(1))) {
                if (!refs.contains(ref)) {
                    refs.add(ref);
                }
            }
            inline.appendReplacement(cleaned, "");
        }
        inline.appendTail(cleaned);
        text = SPACE_BEFORE_PUNCTUATION.matcher(cleaned.toString().strip()).replaceAll("$1");
        return text.isEmpty() ? null : new Claim(text, refs);
    }

    /** {@code "R1, F1-F3; E3"} → R1, F1, F2, F3, E3 (a range keeps the letter of its start). */
    private static List<String> expandRanges(String group) {
        List<String> refs = new ArrayList<>();
        for (String part : group.split("\\s*(?:[,;/]|\\band\\b|\\bund\\b)\\s*")) {
            Matcher range = RANGE.matcher(part.strip().toUpperCase(Locale.ROOT));
            if (range.matches() && range.group(3) != null) {
                int from = Integer.parseInt(range.group(2));
                int to = Integer.parseInt(range.group(3));
                for (int i = from; i <= to && to - from < 30; i++) {
                    refs.add(range.group(1) + i);
                }
            } else if (!part.isBlank()) {
                refs.add(part.strip().toUpperCase(Locale.ROOT));
            }
        }
        return refs;
    }

    private static FindingSeverity severity(String value) {
        try {
            FindingSeverity severity = FindingSeverity.valueOf(value.strip().toUpperCase(Locale.ROOT));
            return severity == FindingSeverity.CONFLICT ? FindingSeverity.NEUTRAL : severity;
        } catch (IllegalArgumentException e) {
            return FindingSeverity.NEUTRAL;
        }
    }
}
