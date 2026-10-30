package com.example.contor.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits an email into the paragraphs report citations point at, addressed as (email, {@link Paragraph#index()}). The
 * text body is used, or the HTML body converted by {@link HtmlText}; {@link EmailTextCleaner} removes quoted history,
 * reply headers, signatures and mobile footers. Paragraphs are separated by blank lines; list items are paragraphs of
 * their own; a short greeting or lead-in ("Hi Tom,") joins the next paragraph; longer paragraphs than
 * {@link #MAX_PARAGRAPH_CHARS} are split at a sentence end. A change to these rules bumps {@link #SEGMENTER_VERSION}:
 * citations keep the version, the paragraph hash and the quote, so older ones are found by their text.
 */
public final class EmailParagraphs {

    public static final int SEGMENTER_VERSION = 1;
    public static final int MAX_PARAGRAPH_CHARS = 1_200;
    private static final int FRAGMENT_CHARS = 40;
    private static final int HASH_CHARS = 16;
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n{2,}");
    private static final Pattern LIST_ITEM = Pattern.compile("^([-*•·]|\\d{1,3}[.)])\\s+\\S.*");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](?=\\s)");
    private static final Pattern SINGLE_QUOTES = Pattern.compile("[‘’‚‛′`]");
    private static final Pattern DOUBLE_QUOTES = Pattern.compile("[“”„‟″«»]");
    private static final Pattern DASHES = Pattern.compile("[‐‑‒–—―−]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private EmailParagraphs() {
    }

    /**
     * @param index position in the email, from 0
     * @param hash  of the {@link #normalize normalized} text, to recognize the paragraph after re-segmenting
     */
    public record Paragraph(int index, String text, String hash) {}

    public static List<Paragraph> split(String bodyText, String bodyHtml) {
        String text = bodyText != null && !bodyText.isBlank() ? bodyText : HtmlText.toText(bodyHtml);
        String cleaned = EmailTextCleaner.clean(text, Integer.MAX_VALUE);
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<String> blocks = new ArrayList<>();
        for (String block : PARAGRAPH_BREAK.split(cleaned)) {
            blocks.addAll(splitListItems(block));
        }
        List<Paragraph> paragraphs = new ArrayList<>();
        for (String block : mergeFragments(blocks)) {
            for (String part : capped(block)) {
                paragraphs.add(new Paragraph(paragraphs.size(), part, hash(part)));
            }
        }
        return List.copyOf(paragraphs);
    }

    /** The index of the first paragraph containing {@code quote} (compared {@link #normalize normalized}). */
    public static Optional<Integer> locate(List<Paragraph> paragraphs, String quote) {
        if (quote == null || quote.isBlank()) {
            return Optional.empty();
        }
        String wanted = normalize(quote);
        return paragraphs.stream()
                .filter(paragraph -> normalize(paragraph.text()).contains(wanted))
                .map(Paragraph::index)
                .findFirst();
    }

    /** Lower case, typographic quotes and dashes as ASCII, whitespace collapsed: how quotes are compared. */
    public static String normalize(String text) {
        String plain = SINGLE_QUOTES.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("'");
        plain = DOUBLE_QUOTES.matcher(plain).replaceAll("\"");
        plain = DASHES.matcher(plain).replaceAll("-");
        return WHITESPACE.matcher(plain).replaceAll(" ").strip();
    }

    private static List<String> splitListItems(String block) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : block.lines().toList()) {
            if (LIST_ITEM.matcher(line).matches() && !current.isEmpty()) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    /** A one-line greeting or lead-in ending in "," or ":" belongs to the paragraph after it. */
    private static List<String> mergeFragments(List<String> blocks) {
        List<String> merged = new ArrayList<>();
        String pending = null;
        for (String block : blocks) {
            String text = pending == null ? block : pending + "\n" + block;
            pending = null;
            if (text.length() <= FRAGMENT_CHARS && text.indexOf('\n') < 0 && (text.endsWith(",") || text.endsWith(":"))) {
                pending = text;
            } else {
                merged.add(text);
            }
        }
        if (pending != null) {
            merged.add(pending);
        }
        return merged;
    }

    private static List<String> capped(String block) {
        List<String> parts = new ArrayList<>();
        String rest = block;
        while (rest.length() > MAX_PARAGRAPH_CHARS) {
            int cut = cutPosition(rest);
            parts.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }

    /** After the last sentence end within the limit, else at the last whitespace, else at the limit. */
    private static int cutPosition(String text) {
        Matcher sentenceEnd = SENTENCE_END.matcher(text);
        int cut = -1;
        while (sentenceEnd.find() && sentenceEnd.end() <= MAX_PARAGRAPH_CHARS) {
            cut = sentenceEnd.end();
        }
        if (cut > 0) {
            return cut;
        }
        for (int i = MAX_PARAGRAPH_CHARS; i > 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return MAX_PARAGRAPH_CHARS;
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalize(text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
