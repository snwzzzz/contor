package com.example.contor.ai.eval;

import com.example.contor.ai.EmailContent;

import java.util.List;
import java.util.Map;

/**
 * One labelled email of the extraction evaluation (a JSON file in {@code ai-eval/synthetic} or the private corpus).
 *
 * @param email         what the model sees (same shape as {@link EmailContent}; {@code id} may be absent)
 * @param expected      what a perfect extraction records
 * @param mustNotProduce things that must never be recorded for this email (counted as forbidden hits)
 * @param needsReply     whether the email needs a written answer from the user (reply triage); {@code null} = not labelled
 */
public record EvalCase(String id, EmailContent email, Expected expected, List<Forbidden> mustNotProduce, Boolean needsReply) {

    public EvalCase {
        expected = expected != null ? expected : new Expected(null, null);
        mustNotProduce = mustNotProduce != null ? mustNotProduce : List.of();
    }

    /** A case without a reply label. */
    public EvalCase(String id, EmailContent email, Expected expected, List<Forbidden> mustNotProduce) {
        this(id, email, expected, mustNotProduce, null);
    }

    /**
     * @param records   expected records per collection slug
     * @param relations expected relations; parsed, scored once relations are extracted
     */
    public record Expected(Map<String, List<ExpectedRecord>> records, List<ExpectedRelation> relations) {

        public Expected {
            records = records != null ? records : Map.of();
            relations = relations != null ? relations : List.of();
        }

        public int recordCount() {
            return records.values().stream().mapToInt(List::size).sum();
        }
    }

    /** A record with its title and the (key) field values worth checking; other fields are not scored. */
    public record ExpectedRecord(String title, Map<String, Object> values) {

        public ExpectedRecord {
            values = values != null ? values : Map.of();
        }
    }

    /** {@code from}/{@code to} are {@code "<collection slug>:<title>"}. */
    public record ExpectedRelation(String type, String from, String to) {
    }

    /** Any produced record of {@code collection}; when {@code title} is set, only similar titles. */
    public record Forbidden(String collection, String title) {
    }
}
