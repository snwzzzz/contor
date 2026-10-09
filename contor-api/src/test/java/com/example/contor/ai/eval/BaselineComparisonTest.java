package com.example.contor.ai.eval;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BaselineComparisonTest {

    private static EvalReport report(Double precision, Double recall, Double fieldAccuracy, Double dateAccuracy, Double falsePositiveRate) {
        return new EvalReport("m", "p", 10, 0,
                Map.of("people", new EvalReport.CollectionScore(4, 4, 3, precision, recall)),
                Map.of(),
                new EvalReport.FieldScore(1, 1, fieldAccuracy),
                new EvalReport.FieldScore(1, 1, dateAccuracy),
                new EvalReport.FalsePositives(4, 1, falsePositiveRate),
                0, new EvalReport.Latency(100, 200));
    }

    private static final EvalReport BASELINE = report(0.8, 0.8, 0.9, 0.9, 0.1);

    @Test
    void a_report_within_the_threshold_has_no_regressions() {
        assertThat(BaselineComparison.regressions(report(0.78, 0.8, 0.88, 0.9, 0.12), BASELINE, 0.05)).isEmpty();
    }

    @Test
    void improvements_are_not_regressions() {
        assertThat(BaselineComparison.regressions(report(1.0, 1.0, 1.0, 1.0, 0.0), BASELINE, 0.05)).isEmpty();
    }

    @Test
    void a_recall_drop_beyond_the_threshold_is_reported_with_the_metric_name() {
        assertThat(BaselineComparison.regressions(report(0.8, 0.6, 0.9, 0.9, 0.1), BASELINE, 0.05))
                .singleElement().asString().contains("people.recall");
    }

    @Test
    void precision_field_and_date_drops_are_reported() {
        assertThat(BaselineComparison.regressions(report(0.5, 0.8, 0.5, 0.5, 0.1), BASELINE, 0.05)).hasSize(3);
    }

    @Test
    void a_higher_false_positive_rate_is_a_regression() {
        assertThat(BaselineComparison.regressions(report(0.8, 0.8, 0.9, 0.9, 0.4), BASELINE, 0.05))
                .singleElement().asString().contains("falsePositiveRate");
    }

    @Test
    void metrics_undefined_in_either_report_are_skipped() {
        assertThat(BaselineComparison.regressions(report(null, 0.8, 0.9, 0.9, null), BASELINE, 0.05)).isEmpty();
        assertThat(BaselineComparison.regressions(report(0.1, 0.1, 0.1, 0.1, 0.9), report(null, null, null, null, null), 0.05)).isEmpty();
    }

    @Test
    void collections_missing_from_the_baseline_are_skipped() {
        EvalReport current = new EvalReport("m", "p", 10, 0,
                Map.of("orders", new EvalReport.CollectionScore(4, 4, 0, 0.0, 0.0)), Map.of(),
                new EvalReport.FieldScore(1, 1, 0.9), new EvalReport.FieldScore(1, 1, 0.9),
                new EvalReport.FalsePositives(4, 1, 0.1), 0, new EvalReport.Latency(1, 1));

        assertThat(BaselineComparison.regressions(current, BASELINE, 0.05)).isEmpty();
    }

    @Test
    void latency_is_never_a_regression() {
        EvalReport slow = new EvalReport("m", "p", 10, 0, BASELINE.collections(), Map.of(), BASELINE.fieldsOverall(), BASELINE.dates(),
                BASELINE.falsePositives(), 0, new EvalReport.Latency(99_000, 99_000));

        assertThat(BaselineComparison.regressions(slow, BASELINE, 0.05)).isEmpty();
    }
}
