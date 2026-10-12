package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiTimingsTest {

    private final AtomicLong nanos = new AtomicLong();
    private final AiTimings timings = new AiTimings(nanos::get);

    private static AiTimings.Call call(int promptTokens, int answerTokens) {
        return new AiTimings.Call("qwen", promptTokens, answerTokens, Duration.ofMillis(10), Duration.ofMillis(200), Duration.ofMillis(900));
    }

    private void advance(long millis) {
        nanos.addAndGet(Duration.ofMillis(millis).toNanos());
    }

    @Test
    void reports_each_stage_with_its_duration_and_model_calls() {
        timings.begin(42L);
        String extracted = timings.stage("extract", () -> {
            advance(1500);
            timings.recordCall(call(2140, 310));
            timings.recordCall(call(900, 20));
            return "items";
        });
        timings.stage("embed", () -> advance(100));
        advance(5);

        AiTimings.Report report = timings.finish().orElseThrow();

        assertThat(extracted).isEqualTo("items");
        assertThat(report.emailId()).isEqualTo(42L);
        assertThat(report.total()).isEqualTo(Duration.ofMillis(1605));
        assertThat(report.stages()).extracting(AiTimings.Stage::name).containsExactly("extract", "embed");
        assertThat(report.stages().get(0).duration()).isEqualTo(Duration.ofMillis(1500));
        assertThat(report.stages().get(0).calls()).hasSize(2);
        assertThat(report.stages().get(1).calls()).isEmpty();
    }

    @Test
    void formats_one_line_with_numbers_only() {
        timings.begin(7L);
        timings.stage("extract", () -> {
            advance(1234);
            timings.recordCall(call(2140, 310));
        });
        timings.stage("reply", () -> advance(0));

        String line = timings.finish().orElseThrow().format();

        assertThat(line).isEqualTo("email 7: extract 1.23s [qwen 2140→310 tok, load 0.01s, prompt 0.20s, answer 0.90s]"
                + ", reply 0.00s, total 1.23s");
    }

    @Test
    void a_stage_that_fails_is_still_reported_and_the_failure_passes_through() {
        timings.begin(1L);

        assertThatThrownBy(() -> timings.stage("extract", () -> {
            advance(300);
            throw new IllegalStateException("model down");
        })).isInstanceOf(IllegalStateException.class);

        AiTimings.Report report = timings.finish().orElseThrow();
        assertThat(report.stages()).singleElement().satisfies(stage -> assertThat(stage.duration()).isEqualTo(Duration.ofMillis(300)));
    }

    @Test
    void outside_a_session_stages_just_run_and_calls_are_ignored() {
        int answer = timings.stage("compose", () -> {
            timings.recordCall(call(1, 1));
            return 3;
        });

        assertThat(answer).isEqualTo(3);
        assertThat(timings.finish()).isEmpty();
    }

    @Test
    void calls_outside_a_stage_are_ignored() {
        timings.begin(1L);
        timings.recordCall(call(1, 1));

        assertThat(timings.finish().orElseThrow().stages()).isEmpty();
    }

    @Test
    void a_nested_stage_counts_towards_the_outer_one() {
        timings.begin(1L);
        timings.stage("reply", () -> timings.stage("triage", () -> {
            advance(50);
            timings.recordCall(call(1, 1));
        }));

        AiTimings.Report report = timings.finish().orElseThrow();
        assertThat(report.stages()).singleElement().satisfies(stage -> {
            assertThat(stage.name()).isEqualTo("reply");
            assertThat(stage.calls()).hasSize(1);
        });
    }

    @Test
    void finish_ends_the_session() {
        timings.begin(1L);
        timings.finish();

        assertThat(timings.finish()).isEmpty();
    }
}
