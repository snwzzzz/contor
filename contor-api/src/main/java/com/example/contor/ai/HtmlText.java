package com.example.contor.ai;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * HTML mail as plain text whose blank lines are the paragraph breaks a reader sees. Paragraph-like blocks ({@code p},
 * headings, lists, tables) end a paragraph, line-like blocks ({@code div}, {@code br}, rows, list items) end a line.
 * Outlook writes every line as its own {@code p} and separates paragraphs with empty ones ("spacers"): when a mail has
 * spacers, {@code p} ends a line and the spacers make the breaks. Head, scripts, styles and quoted history
 * ({@code blockquote}, Gmail's {@code gmail_quote}) are dropped; entities are decoded.
 */
public final class HtmlText {

    private static final Set<String> PARAGRAPHS = Set.of("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "pre", "hr");
    private static final Set<String> LINES = Set.of("div", "tr", "li", "section", "article", "header", "footer", "address", "dl", "dt",
            "dd", "center", "form", "fieldset", "main", "nav", "aside", "figure", "figcaption", "caption", "tbody", "thead", "tfoot");
    private static final char NBSP = ' ';
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private HtmlText() {
    }

    public static String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Element body = Jsoup.parse(html).body();
        body.select("script, style, title, blockquote, .gmail_quote").remove();
        boolean spacers = body.select("p").stream()
                .anyMatch(paragraph -> paragraph.text().replace(NBSP, ' ').isBlank() && paragraph.select("img").isEmpty());
        StringBuilder text = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    appendText(text, textNode.getWholeText());
                } else if (node instanceof Element element) {
                    String tag = element.normalName();
                    if (tag.equals("br")) {
                        text.append('\n');
                    } else if (tag.equals("li")) {
                        endLine(text, 1);
                        text.append(listMarker(element));
                    } else if ((tag.equals("td") || tag.equals("th")) && element.previousElementSibling() != null) {
                        text.append(" | ");
                    } else if (PARAGRAPHS.contains(tag) || LINES.contains(tag)) {
                        endLine(text, 1);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element) {
                    String tag = element.normalName();
                    if (PARAGRAPHS.contains(tag)) {
                        endLine(text, spacers && tag.equals("p") ? 1 : 2);
                    } else if (LINES.contains(tag)) {
                        endLine(text, 1);
                    }
                }
            }
        }, body);
        StringBuilder lines = new StringBuilder();
        text.toString().lines().map(String::strip).forEach(line -> lines.append(line).append('\n'));
        return BLANK_LINES.matcher(lines).replaceAll("\n\n").strip();
    }

    /**
     * Text as written, whitespace collapsed. Whitespace-only nodes are formatting between tags and only separate words
     * within a line; a non-breaking space is content (Outlook's empty "spacer" paragraphs hold one).
     */
    private static void appendText(StringBuilder text, String raw) {
        boolean lineStart = text.isEmpty() || text.charAt(text.length() - 1) == '\n';
        if (raw.isBlank() && raw.indexOf(NBSP) < 0) {
            if (!lineStart) {
                text.append(' ');
            }
            return;
        }
        text.append(WHITESPACE.matcher(raw).replaceAll(" "));
    }

    /** Ends the current line with at least {@code breaks} line breaks (2 = paragraph break); nothing at the start. */
    private static void endLine(StringBuilder text, int breaks) {
        if (text.isEmpty()) {
            return;
        }
        int trailing = 0;
        while (trailing < text.length() && text.charAt(text.length() - 1 - trailing) == '\n') {
            trailing++;
        }
        text.append("\n".repeat(Math.max(0, breaks - trailing)));
    }

    /** {@code "1. "} in ordered lists (by position), {@code "- "} otherwise. */
    private static String listMarker(Element item) {
        Element list = item.parent();
        return list != null && list.normalName().equals("ol") ? (item.elementSiblingIndex() + 1) + ". " : "- ";
    }
}
