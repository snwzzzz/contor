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
 * A report as the model drafted it (prompt {@code report-v1}), before the fact check. Parsing is lenient about shape
 * and strict about content: blank sentences, findings without a title or a sentence and severities the model may not
 * set (CONFLICT is only set by the fact check) are dropped or neutralized, and every list is capped.
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

    /** Whether the model wrote no sentence at all. */
    public boolean isEmpty() {
        return summary.isEmpty() && findings.isEmpty() && openQuestions.isEmpty() && nextSteps.isEmpty() && customSections.isEmpty();
    }

    /** One sentence and the source tokens it cites. */
    public record Claim(String text, List<String> refs) {}

    public record Finding(String title, FindingSeverity severity, List<Claim> claims, List<ClaimChecks.Quote> quotes) {}

    public record Section(String title, List<Claim> claims) {}

    public static ReportDraft parse(JsonNode json) {
        List<Finding> findings = new ArrayList<>();
        for (JsonNode node : json.path("findings")) {
            String title = node.path("title").asString("").strip();
            List<Claim> claims = claims(node.path("claims"), MAX_FINDING_CLAIMS);
            if (title.isEmpty() || claims.isEmpty()) {
                continue;
            }
            List<ClaimChecks.Quote> quotes = new ArrayList<>();
            for (JsonNode quote : node.path("quotes")) {
                String text = quote.path("text").asString("").strip();
                if (!text.isEmpty() && quotes.size() < MAX_QUOTES) {
                    quotes.add(new ClaimChecks.Quote(quote.path("ref").asString(""), text));
                }
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
        return new ReportDraft(json.path("title").asString("").strip(), claims(json.path("summary"), MAX_SUMMARY), findings,
                claims(json.path("openQuestions"), MAX_LIST), claims(json.path("nextSteps"), MAX_LIST), sections);
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
                "title", JsonSchemas.string(),
                "summary", JsonSchemas.array(claim),
                "findings", JsonSchemas.array(finding),
                "openQuestions", JsonSchemas.array(claim),
                "nextSteps", JsonSchemas.array(claim),
                "customSections", JsonSchemas.array(section)));
    }

    private static List<Claim> claims(JsonNode array, int max) {
        List<Claim> claims = new ArrayList<>();
        for (JsonNode node : array) {
            String text = node.path("text").asString("").strip();
            if (text.isEmpty()) {
                continue;
            }
            List<String> refs = new ArrayList<>();
            for (JsonNode ref : node.path("refs")) {
                if (ref.isString()) {
                    refs.add(ref.asString().strip().replace("[", "").replace("]", ""));
                }
            }
            // Models also write their sources into the sentence: "(R1, F1-F3)", "[E3; P4.2]".
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
            if (text.isEmpty()) {
                continue;
            }
            claims.add(new Claim(text, refs));
            if (claims.size() == max) {
                break;
            }
        }
        return claims;
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
