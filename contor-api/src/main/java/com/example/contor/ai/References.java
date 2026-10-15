package com.example.contor.ai;

import com.example.contor.ai.EvidencePack.Source;
import com.example.contor.domain.CitationType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The references of one report sentence: which sources of the {@link EvidencePack} it points at. Nothing is checked or
 * removed — a sentence stays as the model wrote it; it gets a reference where there is one. Unknown references are
 * dropped, a whole email ({@code E3}) stands for its paragraphs, a quote is kept only when it is verbatim in its email
 * paragraph (so a shown quote is always real), and codes the model wrote into the text become references and readable
 * names ("Record R1 shows…" → "Order 4711 shows…", "email P2.1" → "the email “Subject”").
 */
public final class References {

    /** "(P1.1, R1:F2)", "[F9]": a bracket that holds only codes. */
    private static final Pattern CODES_IN_BRACKETS = Pattern.compile(
            "\\s*[(\\[]\\s*(?:(?:[RE]\\d+:)?[RFLPE]\\d+(?:\\.\\d+)?)(?:\\s*[,;/]\\s*(?:[RE]\\d+:)?[RFLPE]\\d+(?:\\.\\d+)?)*\\s*[)\\]]");
    /** A code in the text, with the noun the model put before it ("record R1", "email P2.1"). */
    private static final Pattern CODE_IN_TEXT = Pattern.compile(
            "(?i)\\b(?:(?:the\\s+)?(?:record|email|e-mail|mail|field|source|connection|paragraph)\\s+)?((?:[RE]\\d+:)?[RFLPE]\\d+(?:\\.\\d+)?)\\b");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+([.,;:!?])");

    private References() {
    }

    /** A verbatim quote from the email paragraph {@code token}. */
    public record Quote(String token, String text) {}

    /**
     * @param text   the sentence with codes turned into names
     * @param refs   the tokens it points at, without repeats (quoted paragraphs and codes from the text included)
     * @param quotes the quotes that are verbatim in their paragraph
     */
    public record Resolved(String text, List<String> refs, List<Quote> quotes) {}

    public static Resolved resolve(String text, List<String> refs, List<Quote> quotes, EvidencePack pack) {
        Set<String> tokens = new LinkedHashSet<>();
        refs.forEach(ref -> tokens.addAll(pack.expand(ref)));
        List<Quote> kept = new ArrayList<>();
        for (Quote quote : quotes) {
            Source source = pack.source(quote.token()).orElse(null);
            if (source != null && source.type() == CitationType.EMAIL_PARAGRAPH && quote.text() != null && !quote.text().isBlank()
                    && EmailParagraphs.normalize(source.text()).contains(EmailParagraphs.normalize(quote.text()))) {
                kept.add(new Quote(source.token(), quote.text().strip()));
                tokens.add(source.token());
            }
        }
        String cleaned = text == null ? "" : text;
        // Codes in the text are references in reading order; in brackets they are removed, elsewhere named.
        CODE_IN_TEXT.matcher(cleaned).results().forEach(code -> tokens.addAll(pack.expand(code.group(1))));
        String withoutBrackets = CODES_IN_BRACKETS.matcher(cleaned).replaceAll("");
        Matcher inText = CODE_IN_TEXT.matcher(withoutBrackets);
        StringBuilder named = new StringBuilder();
        while (inText.find()) {
            List<String> found = pack.expand(inText.group(1));
            String name = found.isEmpty() ? "" : name(pack.source(found.getFirst()).orElseThrow());
            inText.appendReplacement(named, Matcher.quoteReplacement(name));
        }
        inText.appendTail(named);
        String result = SPACE_BEFORE_PUNCTUATION.matcher(SPACES.matcher(named.toString()).replaceAll(" ").strip()).replaceAll("$1");
        if (!result.isEmpty() && !result.equals(cleaned.strip())) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }
        return new Resolved(result, List.copyOf(tokens), kept);
    }

    /** How a source reads in a sentence. */
    private static String name(Source source) {
        return switch (source.type()) {
            case RECORD -> Optional.ofNullable(source.recordTitle()).orElse(source.label());
            case FIELD -> "the " + source.label();
            case RELATION -> "the " + source.label() + " connection";
            case EMAIL_PARAGRAPH -> "the email “" + Optional.ofNullable(source.label()).orElse("") + "”";
        };
    }
}
