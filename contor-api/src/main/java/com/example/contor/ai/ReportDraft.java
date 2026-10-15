package com.example.contor.ai;

import com.example.contor.domain.ReportBlockKind;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * A report as the model drafted it: a Markdown {@code document} read by {@link ReportMarkdown} into sections of
 * paragraphs, lists, quotes and tables whose sentences, items and cells carry the source codes they cite; the
 * {@code title} (or the document's top heading); and whether the record's timeline helps with the request.
 */
public record ReportDraft(String title, List<Section> sections, boolean timeline) {

    /** One sentence, list item or table cell and the source tokens it cites. */
    public record Claim(String text, List<String> refs) {}

    /** @param title empty for the text before the first heading */
    public record Section(String title, List<Block> blocks) {}

    /**
     * @param columns the header of a table (may be empty)
     * @param items   sentences of a paragraph, list items, or the quote
     * @param rows    the cells of a table, row by row
     */
    public record Block(ReportBlockKind kind, List<String> columns, List<Claim> items, List<List<Claim>> rows) {}

    /** Whether the model wrote nothing. */
    public boolean isEmpty() {
        return sections.isEmpty();
    }

    public static ReportDraft parse(JsonNode json) {
        ReportMarkdown.Document document = ReportMarkdown.parse(json.path("document").asString(""));
        String title = json.path("title").asString("").strip();
        return new ReportDraft(title.isEmpty() ? document.title() : title, document.sections(), json.path("timeline").asBoolean(false));
    }

    /** The JSON schema the model answers with: the document first, so it is written before anything else. */
    public static Map<String, Object> schema() {
        return JsonSchemas.object(JsonSchemas.properties(
                "document", JsonSchemas.string(),
                "title", JsonSchemas.string(),
                "timeline", JsonSchemas.bool()));
    }
}
