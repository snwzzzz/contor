package com.example.contor.ai.eval;

import java.util.Map;
import java.util.TreeMap;

/**
 * Scores of one evaluation run. Ratios are {@code null} when undefined (no produced / expected / nothing-mails), which
 * keeps them out of baseline comparisons.
 *
 * @param collections   record precision and recall per collection slug
 * @param fields        exact-or-normalized accuracy per {@code collection.field} on matched records
 * @param fieldsOverall all field comparisons together
 * @param dates         date correctness of events and to-dos: expected date fields that were extracted correctly
 *                      (the dates of missed records count as wrong)
 * @param falsePositives share of "nothing" mails (nothing expected) that still produced a record
 * @param forbiddenHits produced records matching a {@code mustNotProduce} entry
 * @param relations     precision and recall of relations (an expected relation counts when both of its records were
 *                      extracted correctly and the same relation type connects them); {@code null} in old reports
 */
public record EvalReport(String model, String promptVersion, int cases, int failedCases,
                         Map<String, CollectionScore> collections, Map<String, FieldScore> fields,
                         FieldScore fieldsOverall, FieldScore dates, FalsePositives falsePositives, int forbiddenHits,
                         Latency latency, CollectionScore relations) {

    /** A report without relation scores (reports written before relations were extracted). */
    public EvalReport(String model, String promptVersion, int cases, int failedCases, Map<String, CollectionScore> collections,
                      Map<String, FieldScore> fields, FieldScore fieldsOverall, FieldScore dates, FalsePositives falsePositives,
                      int forbiddenHits, Latency latency) {
        this(model, promptVersion, cases, failedCases, collections, fields, fieldsOverall, dates, falsePositives, forbiddenHits, latency, null);
    }

    public record CollectionScore(int expected, int produced, int truePositives, Double precision, Double recall) {
    }

    public record FieldScore(int correct, int total, Double accuracy) {
    }

    public record FalsePositives(int nothingCases, int withOutput, Double rate) {
    }

    public record Latency(long p50Millis, long p95Millis) {
    }

    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# Extraction eval: ").append(model).append(" / ").append(promptVersion).append("\n\n")
                .append(cases).append(" cases, ").append(failedCases).append(" failed model calls\n\n")
                .append("| Collection | Expected | Produced | TP | Precision | Recall |\n|---|---|---|---|---|---|\n");
        new TreeMap<>(collections).forEach((slug, score) -> md.append("| ").append(slug).append(" | ").append(score.expected())
                .append(" | ").append(score.produced()).append(" | ").append(score.truePositives()).append(" | ")
                .append(percent(score.precision())).append(" | ").append(percent(score.recall())).append(" |\n"));
        if (relations != null) {
            md.append("\n- Relations: precision ").append(percent(relations.precision())).append(", recall ").append(percent(relations.recall()))
                    .append(" (").append(relations.truePositives()).append('/').append(relations.expected()).append(" expected, ")
                    .append(relations.produced()).append(" produced)\n");
        }
        md.append("\n| Field | Correct | Total | Accuracy |\n|---|---|---|---|\n");
        new TreeMap<>(fields).forEach((name, score) -> md.append("| ").append(name).append(" | ").append(score.correct())
                .append(" | ").append(score.total()).append(" | ").append(percent(score.accuracy())).append(" |\n"));
        md.append("\n- All fields: ").append(percent(fieldsOverall.accuracy())).append(" (").append(fieldsOverall.correct())
                .append('/').append(fieldsOverall.total()).append(")\n")
                .append("- Dates of events and to-dos: ").append(percent(dates.accuracy())).append(" (").append(dates.correct())
                .append('/').append(dates.total()).append(")\n")
                .append("- False positives on \"nothing\" mails: ").append(percent(falsePositives.rate())).append(" (")
                .append(falsePositives.withOutput()).append('/').append(falsePositives.nothingCases()).append(")\n")
                .append("- Forbidden records produced: ").append(forbiddenHits).append('\n')
                .append("- Latency: p50 ").append(latency.p50Millis()).append(" ms, p95 ").append(latency.p95Millis()).append(" ms\n");
        return md.toString();
    }

    private static String percent(Double ratio) {
        return ratio == null ? "n/a" : "%.1f%%".formatted(ratio * 100);
    }
}
