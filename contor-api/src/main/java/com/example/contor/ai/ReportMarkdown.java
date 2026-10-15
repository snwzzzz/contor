package com.example.contor.ai;

import com.example.contor.ai.ReportDraft.Block;
import com.example.contor.ai.ReportDraft.Claim;
import com.example.contor.ai.ReportDraft.Section;
import com.example.contor.domain.ReportBlockKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Markdown document the model writes into sections (one per heading; the text before the first heading is an
 * untitled section) of blocks: paragraphs (split into sentences), bullet and numbered lists, quotes and tables. Source
 * codes in brackets ("[P2.1]", "(R1, F1-F3)") become the refs of the sentence, list item or table cell they follow and
 * leave the text; inline emphasis ({@code **bold**}) stays for the page to render. Lenient about shape: a line that is
 * only bold text is a heading, a leading top-level heading is the title, nested list items join their list, rules and
 * code fences are ignored, blank headings and empty sections are dropped, and everything is capped.
 */
public final class ReportMarkdown {

    public static final int MAX_SECTIONS = 12;
    public static final int MAX_BLOCKS = 15;
    public static final int MAX_ITEMS = 20;
    public static final int MAX_ROWS = 30;
    public static final int MAX_COLUMNS = 8;

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})(?:\\s+(.*?))?\\s*#*$");
    private static final Pattern BOLD_LINE = Pattern.compile("^(?:\\*\\*|__)(.+?)(?:\\*\\*|__)\\s*:?$");
    private static final Pattern RULE = Pattern.compile("^(?:-{3,}|\\*{3,}|_{3,})$");
    private static final Pattern BULLET = Pattern.compile("^[-*+•]\\s+(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\d{1,3}[.)]\\s+(.*)$");
    private static final Pattern CELL_SEPARATOR = Pattern.compile("(?<!\\\\)\\|");
    private static final Pattern SEPARATOR_CELL = Pattern.compile(":?-+:?");

    /** A source token, also with its record or email in front ({@code R1:F5}, {@code E3:P3.1}), or a figure row ({@code A1.2}). */
    private static final String TOKEN = "(?:(?:[RE]\\d+:)?[RFLPE]\\d+(?:\\.\\d+)?|A\\d+\\.\\d+)";
    /** A bracket that holds nothing but source tokens, ranges and separators. */
    private static final Pattern INLINE_REFS = Pattern.compile("(?i)\\s*[(\\[]\\s*(" + TOKEN + "(?:\\s*-\\s*[RFLPE]?\\d+)?"
            + "(?:\\s*(?:[,;/]|\\band\\b|\\bund\\b)\\s*" + TOKEN + "(?:\\s*-\\s*[RFLPE]?\\d+)?)*)\\s*[)\\]]");
    private static final Pattern RANGE = Pattern.compile("([RFLPE])(\\d+)(?:\\s*-\\s*[RFLPE]?(\\d+))?");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+([.,;:!?])");
    /** A sentence ends after a letter, a bracket or a quote mark and . ! ? — not after a number ("23.07. Die …"). */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[\\p{L})\\]\"”'][.!?])\\s+(?=[\\p{Lu}\\p{N}„\"“*])");

    private ReportMarkdown() {
    }

    /** @param title a top-level heading the document starts with; empty when there is none */
    public record Document(String title, List<Section> sections) {}

    public static Document parse(String markdown) {
        Parser parser = new Parser();
        if (markdown != null) {
            for (String line : markdown.replace("\r\n", "\n").split("\n", -1)) {
                parser.line(line);
            }
        }
        return parser.finish();
    }

    private static final class Parser {

        private final List<Section> sections = new ArrayList<>();
        private String title = "";
        private String sectionTitle = "";
        private List<Block> blocks = new ArrayList<>();
        private final List<String> paragraph = new ArrayList<>();
        private final List<String> quote = new ArrayList<>();
        private final List<String> table = new ArrayList<>();
        private final List<StringBuilder> items = new ArrayList<>();
        private ReportBlockKind listKind;

        void line(String line) {
            String text = line.strip();
            if (text.startsWith("```")) {
                return;
            }
            if (text.isEmpty()) {
                flushParagraph();
                flushQuote();
                flushTable();
                return; // a list stays open: models often put blank lines between items
            }
            String heading = heading(text);
            if (heading != null) {
                flushAll();
                if (heading.isEmpty()) {
                    return;
                }
                if (text.startsWith("# ") && title.isEmpty() && sections.isEmpty() && blocks.isEmpty()) {
                    title = heading;
                    return;
                }
                startSection(heading);
                return;
            }
            if (RULE.matcher(text).matches()) {
                flushAll();
                return;
            }
            if (text.startsWith("|")) {
                flushParagraph();
                flushQuote();
                flushList();
                table.add(text);
                return;
            }
            flushTable();
            if (text.startsWith(">")) {
                flushParagraph();
                flushList();
                quote.add(text.substring(1).strip());
                return;
            }
            flushQuote();
            Matcher bullet = BULLET.matcher(text);
            Matcher numbered = NUMBERED.matcher(text);
            if (bullet.matches() || numbered.matches()) {
                ReportBlockKind kind = bullet.matches() ? ReportBlockKind.BULLETS : ReportBlockKind.NUMBERED;
                boolean nested = indent(line) >= 2;
                if (listKind == null || (!nested && kind != listKind)) {
                    flushParagraph();
                    flushList();
                    listKind = kind;
                }
                items.add(new StringBuilder((bullet.matches() ? bullet : numbered).group(1).strip()));
                return;
            }
            if (listKind != null && paragraph.isEmpty() && indent(line) >= 2 && !items.isEmpty()) {
                items.getLast().append(' ').append(text);
                return;
            }
            flushList();
            paragraph.add(text);
        }

        Document finish() {
            flushAll();
            endSection();
            return new Document(title, sections);
        }

        private void startSection(String heading) {
            endSection();
            sectionTitle = heading;
        }

        private void endSection() {
            if (!blocks.isEmpty() && sections.size() < MAX_SECTIONS) {
                sections.add(new Section(sectionTitle, List.copyOf(blocks)));
            }
            blocks = new ArrayList<>();
        }

        private void flushAll() {
            flushParagraph();
            flushQuote();
            flushTable();
            flushList();
        }

        private void flushParagraph() {
            if (paragraph.isEmpty()) {
                return;
            }
            List<Claim> sentences = new ArrayList<>();
            for (String sentence : SENTENCE_END.split(String.join(" ", paragraph))) {
                Claim claim = claim(sentence, new ArrayList<>());
                if (claim != null) {
                    sentences.add(claim);
                }
            }
            paragraph.clear();
            add(new Block(ReportBlockKind.PARAGRAPH, List.of(), sentences, List.of()));
        }

        private void flushQuote() {
            if (quote.isEmpty()) {
                return;
            }
            Claim claim = claim(String.join(" ", quote), new ArrayList<>());
            quote.clear();
            if (claim != null) {
                add(new Block(ReportBlockKind.QUOTE, List.of(), List.of(claim), List.of()));
            }
        }

        private void flushList() {
            if (listKind == null) {
                return;
            }
            List<Claim> claims = new ArrayList<>();
            for (StringBuilder item : items) {
                Claim claim = claim(item.toString(), new ArrayList<>());
                if (claim != null && claims.size() < MAX_ITEMS) {
                    claims.add(claim);
                }
            }
            add(new Block(listKind, List.of(), claims, List.of()));
            items.clear();
            listKind = null;
        }

        private void flushTable() {
            if (table.isEmpty()) {
                return;
            }
            List<List<String>> lines = table.stream().map(ReportMarkdown::cells).toList();
            table.clear();
            List<String> columns = List.of();
            int start = 0;
            if (lines.size() > 1 && separator(lines.get(1))) {
                columns = lines.getFirst().stream().map(cell -> {
                    Claim claim = claim(cell, new ArrayList<>());
                    return claim != null ? claim.text() : "";
                }).toList();
                start = 2;
            }
            List<List<String>> data = lines.subList(start, lines.size()).stream().filter(row -> !separator(row)).limit(MAX_ROWS).toList();
            int width = Math.min(MAX_COLUMNS, Math.max(columns.size(), data.stream().mapToInt(List::size).max().orElse(0)));
            if (width == 0) {
                return;
            }
            List<List<Claim>> rows = new ArrayList<>();
            for (List<String> row : data) {
                List<Claim> cells = new ArrayList<>();
                for (int i = 0; i < width; i++) {
                    List<String> refs = new ArrayList<>();
                    Claim claim = i < row.size() ? claim(row.get(i), refs) : null;
                    cells.add(claim != null ? claim : new Claim("", List.copyOf(refs)));
                }
                rows.add(cells);
            }
            List<String> header = columns.size() > width ? columns.subList(0, width) : columns;
            add(new Block(ReportBlockKind.TABLE, List.copyOf(header), List.of(), rows));
        }

        private void add(Block block) {
            boolean empty = block.kind() == ReportBlockKind.TABLE ? block.rows().isEmpty() : block.items().isEmpty();
            if (!empty && blocks.size() < MAX_BLOCKS) {
                blocks.add(block);
            }
        }
    }

    /** The heading text of a line ("## Causes", "**Next steps:**"); empty for a blank heading, {@code null} for no heading. */
    private static String heading(String line) {
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            return clean(heading.group(2));
        }
        Matcher bold = BOLD_LINE.matcher(line);
        if (bold.matches() && !bold.group(1).contains("**") && !bold.group(1).contains("__")) {
            return clean(bold.group(1));
        }
        return null;
    }

    private static String clean(String heading) {
        if (heading == null) {
            return "";
        }
        String text = heading.replace("**", "").replace("__", "").strip();
        return text.endsWith(":") ? text.substring(0, text.length() - 1).strip() : text;
    }

    private static int indent(String line) {
        int spaces = 0;
        while (spaces < line.length() && (line.charAt(spaces) == ' ' || line.charAt(spaces) == '\t')) {
            spaces += line.charAt(spaces) == '\t' ? 4 : 1;
        }
        return spaces;
    }

    private static List<String> cells(String line) {
        String inner = line.strip();
        inner = inner.startsWith("|") ? inner.substring(1) : inner;
        inner = inner.endsWith("|") && !inner.endsWith("\\|") ? inner.substring(0, inner.length() - 1) : inner;
        List<String> cells = new ArrayList<>();
        for (String cell : CELL_SEPARATOR.split(inner, -1)) {
            cells.add(cell.replace("\\|", "|").strip());
        }
        return cells;
    }

    private static boolean separator(List<String> cells) {
        return cells.stream().allMatch(cell -> SEPARATOR_CELL.matcher(cell).matches());
    }

    /** A sentence without the codes written into it ("(R1, F1-F3)", "[E3; P4.2]"), which join {@code refs}; null when blank. */
    static Claim claim(String raw, List<String> refs) {
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
        return text.isEmpty() ? null : new Claim(text, List.copyOf(refs));
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
}
