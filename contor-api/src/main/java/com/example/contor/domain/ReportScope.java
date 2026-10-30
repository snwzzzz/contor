package com.example.contor.domain;

import java.util.HashSet;
import java.util.List;

/**
 * What a report is about: the anchor record and the connected records the user picked (each addressed by collection and
 * record id), how far connections were followed, and the emails that were used — or, for a report over a whole
 * collection, the table's filters and search ({@link #collection}) and no records.
 *
 * @param records    at most {@link #MAX_RECORDS}, no duplicates; empty only for a collection report
 * @param depth      0 (the record alone) to {@link #MAX_DEPTH} (connections of connections)
 * @param emailIds   at most {@link #MAX_EMAILS}; {@code null} = none
 * @param collection set for a report over the report's collection; {@code null} for a record report
 */
public record ReportScope(List<RecordRef> records, int depth, List<Long> emailIds, CollectionScope collection) {

    public static final int MAX_RECORDS = 25;
    public static final int MAX_DEPTH = 2;
    public static final int MAX_EMAILS = 60;

    public ReportScope {
        if (records == null || (records.isEmpty() && collection == null)) {
            throw new IllegalArgumentException("a report needs at least one record");
        }
        if (records.size() > MAX_RECORDS) {
            throw new IllegalArgumentException("a report covers at most " + MAX_RECORDS + " records");
        }
        if (new HashSet<>(records).size() != records.size()) {
            throw new IllegalArgumentException("a record may be in a report only once");
        }
        if (depth < 0 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("depth must be between 0 and " + MAX_DEPTH);
        }
        emailIds = emailIds == null ? List.of() : List.copyOf(emailIds);
        if (emailIds.size() > MAX_EMAILS) {
            throw new IllegalArgumentException("a report uses at most " + MAX_EMAILS + " emails");
        }
        records = List.copyOf(records);
    }

    /** A record report. */
    public ReportScope(List<RecordRef> records, int depth, List<Long> emailIds) {
        this(records, depth, emailIds, null);
    }

    /** A report over the records of its collection that match {@code collection}. */
    public static ReportScope ofCollection(CollectionScope collection) {
        if (collection == null) {
            throw new IllegalArgumentException("a collection report needs its collection scope");
        }
        return new ReportScope(List.of(), 0, List.of(), collection);
    }

    public boolean coversCollection() {
        return collection != null;
    }

    /** The same scope for a new run (a follow-up or regenerate): the emails are collected again. */
    public ReportScope withoutEmails() {
        return new ReportScope(records, depth, List.of(), collection);
    }

    /** A record of any collection (record ids are unique per collection table only). */
    public record RecordRef(Long collectionId, Long recordId) {

        public RecordRef {
            if (collectionId == null || collectionId <= 0 || recordId == null || recordId <= 0) {
                throw new IllegalArgumentException("record reference needs positive ids");
            }
        }
    }

    /**
     * Which records of the collection a collection report covers: the table's filters ({@code field:op:value}, as in
     * {@code GET /records}) and search when the report was asked.
     *
     * @param filters at most {@link #MAX_FILTERS}, none blank; {@code null} = none
     * @param search  {@code null} or blank = no search
     */
    public record CollectionScope(List<String> filters, String search) {

        public static final int MAX_FILTERS = 20;

        public CollectionScope {
            filters = filters == null ? List.of() : List.copyOf(filters);
            if (filters.size() > MAX_FILTERS) {
                throw new IllegalArgumentException("a report takes at most " + MAX_FILTERS + " filters");
            }
            if (filters.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("a filter must not be empty");
            }
            search = search == null || search.isBlank() ? null : search.strip();
        }
    }
}
