package com.example.contor.ai.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compares a run against the committed baseline. Quality only: latency depends on the machine and never fails a run.
 * <p>
 * Identical runs differ by a record here and there, and in a small collection one record moves a ratio by more than the
 * threshold (1/13 ≈ 0.08 for 13 to-dos). With a tolerance of {@code n} records a metric only regresses when it is still
 * worse than the baseline after giving the current run {@code n} records the benefit of the doubt.
 */
final class BaselineComparison {

    private BaselineComparison() {
    }

    /** Human-readable regressions: a quality metric that dropped (or the false-positive rate that rose) by more than {@code threshold}. */
    static List<String> regressions(EvalReport current, EvalReport baseline, double threshold) {
        return regressions(current, baseline, threshold, 0);
    }

    /** As {@link #regressions(EvalReport, EvalReport, double)}, judging each metric as if {@code tolerance} records were better. */
    static List<String> regressions(EvalReport current, EvalReport baseline, double threshold, int tolerance) {
        List<String> found = new ArrayList<>();
        Map<String, EvalReport.CollectionScore> baselineCollections = new TreeMap<>(baseline.collections());
        new TreeMap<>(current.collections()).forEach((slug, score) -> {
            EvalReport.CollectionScore before = baselineCollections.get(slug);
            if (before != null) {
                lowerIsWorse(found, slug + ".precision", before.precision(), score.precision(), precision(score, tolerance), threshold, tolerance);
                lowerIsWorse(found, slug + ".recall", before.recall(), score.recall(), recall(score, tolerance), threshold, tolerance);
            }
        });
        if (current.relations() != null && baseline.relations() != null) {
            lowerIsWorse(found, "relations.precision", baseline.relations().precision(), current.relations().precision(),
                    precision(current.relations(), tolerance), threshold, tolerance);
            lowerIsWorse(found, "relations.recall", baseline.relations().recall(), current.relations().recall(),
                    recall(current.relations(), tolerance), threshold, tolerance);
        }
        lowerIsWorse(found, "fields.accuracy", baseline.fieldsOverall().accuracy(), current.fieldsOverall().accuracy(),
                accuracy(current.fieldsOverall(), tolerance), threshold, tolerance);
        lowerIsWorse(found, "dates.accuracy", baseline.dates().accuracy(), current.dates().accuracy(),
                accuracy(current.dates(), tolerance), threshold, tolerance);
        Double before = baseline.falsePositives().rate();
        Double now = current.falsePositives().rate();
        Double lenient = falsePositiveRate(current.falsePositives(), tolerance);
        if (before != null && now != null && lenient != null && lenient - before > threshold) {
            found.add("falsePositiveRate rose from %.3f to %.3f (threshold %.3f%s)".formatted(before, now, threshold, note(tolerance)));
        }
        return found;
    }

    /** {@code lenient} is {@code now} with the tolerance applied; the message shows the real value. */
    private static void lowerIsWorse(List<String> found, String metric, Double before, Double now, Double lenient, double threshold,
                                     int tolerance) {
        if (before != null && now != null && lenient != null && before - lenient > threshold) {
            found.add("%s dropped from %.3f to %.3f (threshold %.3f%s)".formatted(metric, before, now, threshold, note(tolerance)));
        }
    }

    private static String note(int tolerance) {
        return tolerance == 0 ? "" : ", still worse with " + (tolerance == 1 ? "one record" : tolerance + " records") + " better";
    }

    /** The better of: {@code n} wrong records were right, or {@code n} wrong records were not produced. */
    private static Double precision(EvalReport.CollectionScore score, int n) {
        if (score.precision() == null || n == 0) return score.precision();
        int wrong = score.produced() - score.truePositives();
        int fixed = Math.min(n, wrong);
        double moreRight = (double) (score.truePositives() + fixed) / score.produced();
        double fewerWrong = score.produced() - fixed == 0 ? 1.0 : (double) score.truePositives() / (score.produced() - fixed);
        return Math.max(moreRight, fewerWrong);
    }

    private static Double recall(EvalReport.CollectionScore score, int n) {
        if (score.recall() == null || n == 0) return score.recall();
        return (double) Math.min(score.expected(), score.truePositives() + n) / score.expected();
    }

    private static Double accuracy(EvalReport.FieldScore score, int n) {
        if (score.accuracy() == null || n == 0) return score.accuracy();
        return (double) Math.min(score.total(), score.correct() + n) / score.total();
    }

    private static Double falsePositiveRate(EvalReport.FalsePositives positives, int n) {
        if (positives.rate() == null || n == 0) return positives.rate();
        return (double) Math.max(0, positives.withOutput() - n) / positives.nothingCases();
    }
}
