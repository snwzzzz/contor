package com.example.contor.domain;

import java.util.List;

/**
 * A paragraph, list, quote or table of a chat answer, in the shape of a report block. Stored as JSON in
 * {@code chat_message.blocks}; the markers point at the message's {@code chat_citation} rows.
 *
 * @param columns TABLE only: the header; empty otherwise
 * @param items   sentences or list items (not TABLE)
 * @param rows    TABLE only: the rows, each a list of cells
 */
public record ChatBlock(ReportBlockKind kind, List<String> columns, List<Item> items, List<List<Item>> rows) {

    public ChatBlock {
        if (kind == null) {
            throw new IllegalArgumentException("block needs a kind");
        }
        columns = columns == null ? List.of() : List.copyOf(columns);
        items = items == null ? List.of() : List.copyOf(items);
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
        if (kind == ReportBlockKind.TABLE ? !items.isEmpty() : !rows.isEmpty()) {
            throw new IllegalArgumentException("only a table has rows, and a table has no items");
        }
    }

    /** A sentence, list item or table cell with the markers of its sources. */
    public record Item(String text, List<Integer> markers) {

        public Item {
            if (text == null) {
                throw new IllegalArgumentException("item needs text");
            }
            markers = markers == null ? List.of() : List.copyOf(markers);
            if (markers.stream().anyMatch(marker -> marker < 1)) {
                throw new IllegalArgumentException("markers are positive");
            }
        }
    }
}
