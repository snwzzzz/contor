package com.example.contor.ai.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Compares a run against the committed baseline. Quality only: latency depends on the machine and never fails a run. */
final class BaselineComparison {

    private BaselineComparison() {
    }

    /** Human-readable regressions: a quality metric that dropped (or the false-positive rate that rose) by more than {@code threshold}. */
    static List<String> regressions(EvalReport current, EvalReport baseline, double threshold) {
        List<String> found = new ArrayList<>();
        Map<String, EvalReport.CollectionScore> baselineCollections = new TreeMap<>(baseline.collections());
        new TreeMap<>(current.collections()).forEach((slug, score) -> {
            EvalReport.CollectionScore before = baselineCollections.get(slug);
            if (before != null) {
                lowerIsWorse(found, slug + ".precision", before.precision(), score.precision(), threshold);
                lowerIsWorse(found, slug + ".recall", before.recall(), score.recall(), threshold);
            }
        });
        lowerIsWorse(found, "fields.accuracy", baseline.fieldsOverall().accuracy(), current.fieldsOverall().accuracy(), threshold);
        lowerIsWorse(found, "dates.accuracy", baseline.dates().accuracy(), current.dates().accuracy(), threshold);
        Double before = baseline.falsePositives().rate();
        Double now = current.falsePositives().rate();
        if (before != null && now != null && now - before > threshold) {
            found.add("falsePositiveRate rose from %.3f to %.3f (threshold %.3f)".formatted(before, now, threshold));
        }
        return found;
    }

    private static void lowerIsWorse(List<String> found, String metric, Double before, Double now, double threshold) {
        if (before != null && now != null && before - now > threshold) {
            found.add("%s dropped from %.3f to %.3f (threshold %.3f)".formatted(metric, before, now, threshold));
        }
    }
}
