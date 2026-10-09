package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EvalScorerTest {

    private static CollectionFieldDto field(String slug, CollectionFieldType type) {
        return new CollectionFieldDto(1L, slug, slug, type, new LinkedHashMap<>(), false, false, 1);
    }

    private static final CollectionDto PEOPLE = new CollectionDto().id(1L).slug("people").name("People")
            .matchKeys(List.of("email"))
            .fields(List.of(field("full_name", CollectionFieldType.TEXT), field("email", CollectionFieldType.EMAIL),
                    field("phone", CollectionFieldType.PHONE)));
    private static final CollectionDto EVENTS = new CollectionDto().id(2L).slug("events").name("Calendar")
            .matchKeys(List.of())
            .fields(List.of(field("starts_at", CollectionFieldType.DATETIME), field("location", CollectionFieldType.TEXT)));
    private static final CollectionDto TASKS = new CollectionDto().id(3L).slug("tasks").name("To-dos")
            .matchKeys(List.of())
            .fields(List.of(field("due_date", CollectionFieldType.DATE)));

    private final EvalScorer scorer = new EvalScorer(List.of(PEOPLE, EVENTS, TASKS));

    private static EvalCase expecting(String id, Map<String, List<EvalCase.ExpectedRecord>> records, EvalCase.Forbidden... forbidden) {
        return new EvalCase(id, null, new EvalCase.Expected(records, List.of()), List.of(forbidden));
    }

    private static EvalCase nothing(String id) {
        return expecting(id, Map.of());
    }

    private static EvalCase.ExpectedRecord expected(String title, Map<String, Object> values) {
        return new EvalCase.ExpectedRecord(title, values);
    }

    private static ProducedRecord produced(String collection, String title, Map<String, Object> values) {
        return new ProducedRecord(collection, title, values);
    }

    private static CaseResult result(String id, long millis, ProducedRecord... records) {
        return new CaseResult(id, List.of(records), millis, null);
    }

    private EvalReport score(List<EvalCase> cases, List<CaseResult> results) {
        return scorer.score("model", "extract-v1", cases, results);
    }

    @Test
    void perfect_extraction_scores_one() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("people", List.of(expected("Anna Berger", Map.of("email", "anna@acme.com")))))),
                List.of(result("a", 100, produced("people", "Anna Berger", Map.of("email", "anna@acme.com")))));

        assertThat(report.collections().get("people").precision()).isEqualTo(1.0);
        assertThat(report.collections().get("people").recall()).isEqualTo(1.0);
        assertThat(report.fieldsOverall().accuracy()).isEqualTo(1.0);
    }

    @Test
    void records_with_match_keys_are_matched_by_key_even_when_the_title_differs() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("people", List.of(expected("Anna Berger", Map.of("email", "Anna@Acme.com")))))),
                List.of(result("a", 100, produced("people", "A. Berger", Map.of("email", "anna@acme.com")))));

        assertThat(report.collections().get("people").truePositives()).isEqualTo(1);
    }

    @Test
    void different_match_keys_do_not_match_even_with_the_same_title() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("people", List.of(expected("Anna Berger", Map.of("email", "anna@acme.com")))))),
                List.of(result("a", 100, produced("people", "Anna Berger", Map.of("email", "other@acme.com")))));

        assertThat(report.collections().get("people").truePositives()).isZero();
    }

    @Test
    void records_without_a_key_fall_back_to_similar_titles() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("tasks", List.of(expected("Send CV to Acme", Map.of()))))),
                List.of(result("a", 100, produced("tasks", "Send CV", Map.of()))));

        assertThat(report.collections().get("tasks").truePositives()).isEqualTo(1);
    }

    @Test
    void unrelated_titles_do_not_match() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("tasks", List.of(expected("Pay invoice 2024-118", Map.of()))))),
                List.of(result("a", 100, produced("tasks", "Book a hotel", Map.of()))));

        assertThat(report.collections().get("tasks").truePositives()).isZero();
        assertThat(report.collections().get("tasks").precision()).isZero();
        assertThat(report.collections().get("tasks").recall()).isZero();
    }

    @Test
    void extra_records_lower_precision_and_missing_records_lower_recall() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("tasks", List.of(expected("Pay invoice", Map.of()), expected("Call Anna", Map.of()))))),
                List.of(result("a", 100, produced("tasks", "Pay invoice", Map.of()), produced("tasks", "Water the plants", Map.of()),
                        produced("tasks", "Sing a song", Map.of()))));

        assertThat(report.collections().get("tasks").precision()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(report.collections().get("tasks").recall()).isEqualTo(0.5);
    }

    @Test
    void one_produced_record_cannot_satisfy_two_expected_records() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("tasks", List.of(expected("Pay invoice", Map.of()), expected("Pay invoice now", Map.of()))))),
                List.of(result("a", 100, produced("tasks", "Pay invoice", Map.of()))));

        assertThat(report.collections().get("tasks").truePositives()).isEqualTo(1);
        assertThat(report.collections().get("tasks").recall()).isEqualTo(0.5);
    }

    @Test
    void precision_is_undefined_without_produced_records_and_recall_without_expected_records() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("tasks", List.of(expected("Pay invoice", Map.of())))), nothing("b")),
                List.of(result("a", 100), result("b", 100, produced("people", "Spam Bot", Map.of()))));

        assertThat(report.collections().get("tasks").precision()).isNull();
        assertThat(report.collections().get("tasks").recall()).isZero();
        assertThat(report.collections().get("people").recall()).isNull();
        assertThat(report.collections().get("people").precision()).isZero();
    }

    @Test
    void fields_are_scored_per_field_on_matched_records() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("people", List.of(expected("Anna", Map.of("email", "anna@acme.com", "phone", "+43 660 1234567")))))),
                List.of(result("a", 100, produced("people", "Anna", Map.of("email", "anna@acme.com", "phone", "+43 660 9999999")))));

        assertThat(report.fields().get("people.email").accuracy()).isEqualTo(1.0);
        assertThat(report.fields().get("people.phone").accuracy()).isZero();
        assertThat(report.fieldsOverall().correct()).isEqualTo(1);
        assertThat(report.fieldsOverall().total()).isEqualTo(2);
    }

    @Test
    void a_missing_field_counts_as_wrong() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("people", List.of(expected("Anna", Map.of("email", "anna@acme.com", "phone", "+43 660 1234567")))))),
                List.of(result("a", 100, produced("people", "Anna", Map.of("email", "anna@acme.com")))));

        assertThat(report.fields().get("people.phone").accuracy()).isZero();
    }

    @Test
    void event_and_task_dates_are_compared_normalized() {
        EvalReport report = score(
                List.of(expecting("a", Map.of(
                        "events", List.of(expected("Interview", Map.of("starts_at", "2026-10-09T09:00:00+02:00"))),
                        "tasks", List.of(expected("Send CV", Map.of("due_date", "2026-10-08")))))),
                List.of(result("a", 100,
                        produced("events", "Interview", Map.of("starts_at", "2026-10-09T07:00:00Z")),
                        produced("tasks", "Send CV", Map.of("due_date", "2026-10-09")))));

        assertThat(report.dates().correct()).isEqualTo(1);
        assertThat(report.dates().total()).isEqualTo(2);
        assertThat(report.dates().accuracy()).isEqualTo(0.5);
    }

    @Test
    void dates_of_missed_events_count_as_wrong() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("events", List.of(expected("Interview", Map.of("starts_at", "2026-10-09T09:00:00+02:00")))))),
                List.of(result("a", 100)));

        assertThat(report.dates().total()).isEqualTo(1);
        assertThat(report.dates().accuracy()).isZero();
    }

    @Test
    void false_positive_rate_counts_nothing_mails_that_produced_something() {
        EvalReport report = score(
                List.of(nothing("a"), nothing("b"), expecting("c", Map.of("tasks", List.of(expected("Pay", Map.of()))))),
                List.of(result("a", 100), result("b", 100, produced("tasks", "Buy now!", Map.of())), result("c", 100, produced("tasks", "Pay", Map.of()))));

        assertThat(report.falsePositives().nothingCases()).isEqualTo(2);
        assertThat(report.falsePositives().withOutput()).isEqualTo(1);
        assertThat(report.falsePositives().rate()).isEqualTo(0.5);
    }

    @Test
    void false_positive_rate_is_undefined_without_nothing_mails() {
        EvalReport report = score(List.of(expecting("c", Map.of("tasks", List.of(expected("Pay", Map.of()))))),
                List.of(result("c", 100, produced("tasks", "Pay", Map.of()))));

        assertThat(report.falsePositives().rate()).isNull();
    }

    @Test
    void forbidden_records_are_counted() {
        EvalReport report = score(
                List.of(expecting("a", Map.of(), new EvalCase.Forbidden("people", null), new EvalCase.Forbidden("tasks", "Buy now"))),
                List.of(result("a", 100, produced("people", "Newsletter Team", Map.of()), produced("tasks", "Buy now!", Map.of()),
                        produced("tasks", "Something else", Map.of()))));

        assertThat(report.forbiddenHits()).isEqualTo(2);
    }

    @Test
    void latency_uses_nearest_rank_percentiles() {
        EvalReport report = score(List.of(nothing("a"), nothing("b"), nothing("c"), nothing("d"), nothing("e")),
                List.of(result("a", 400), result("b", 100), result("c", 1000), result("d", 300), result("e", 200)));

        assertThat(report.latency().p50Millis()).isEqualTo(300);
        assertThat(report.latency().p95Millis()).isEqualTo(1000);
    }

    @Test
    void failed_cases_are_counted_and_score_as_producing_nothing() {
        EvalReport report = score(
                List.of(expecting("a", Map.of("tasks", List.of(expected("Pay", Map.of()))))),
                List.of(new CaseResult("a", List.of(), 50, "Ollama timeout")));

        assertThat(report.failedCases()).isEqualTo(1);
        assertThat(report.collections().get("tasks").recall()).isZero();
    }

    @Test
    void model_prompt_version_and_case_count_are_reported() {
        EvalReport report = scorer.score("qwen:9b", "extract-v3", List.of(nothing("a")), List.of(result("a", 10)));

        assertThat(report.model()).isEqualTo("qwen:9b");
        assertThat(report.promptVersion()).isEqualTo("extract-v3");
        assertThat(report.cases()).isEqualTo(1);
    }

    @Test
    void markdown_summary_lists_the_headline_numbers() {
        EvalReport report = scorer.score("qwen:9b", "extract-v1",
                List.of(expecting("a", Map.of("tasks", List.of(expected("Pay", Map.of()))))),
                List.of(result("a", 100, produced("tasks", "Pay", Map.of()))));

        assertThat(report.toMarkdown()).contains("qwen:9b", "tasks").doesNotContain("null");
    }
}
