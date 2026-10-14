package com.example.contor.domain;

import java.util.HashSet;
import java.util.List;

/**
 * What a report is about: the anchor record and the connected records the user picked (each addressed by collection and
 * record id), how far connections were followed, and the emails that were used.
 *
 * @param records  at most {@link #MAX_RECORDS}, no duplicates
 * @param depth    0 (the record alone) to {@link #MAX_DEPTH} (connections of connections)
 * @param emailIds at most {@link #MAX_EMAILS}; {@code null} = none
 */
public record ReportScope(List<RecordRef> records, int depth, List<Long> emailIds) {

    public static final int MAX_RECORDS = 25;
    public static final int MAX_DEPTH = 2;
    public static final int MAX_EMAILS = 60;

    public ReportScope {
        if (records == null || records.isEmpty()) {
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

    /** A record of any collection (record ids are unique per collection table only). */
    public record RecordRef(Long collectionId, Long recordId) {

        public RecordRef {
            if (collectionId == null || collectionId <= 0 || recordId == null || recordId <= 0) {
                throw new IllegalArgumentException("record reference needs positive ids");
            }
        }
    }
}
