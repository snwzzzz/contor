package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CachingLlmClientTest {

    private static final JsonNode SCHEMA = JsonMapper.builder().build().readTree("{\"type\":\"object\"}");
    private static final JsonNode OTHER_SCHEMA = JsonMapper.builder().build().readTree("{\"type\":\"array\"}");

    @TempDir
    Path dir;

    /** Answers "answer-N" for the N-th call and remembers the user prompts it saw. */
    private final List<String> calls = new ArrayList<>();
    private final LlmClient model = (llmModel, system, user, schema) -> {
        calls.add(user);
        return "answer-" + calls.size();
    };

    @Test
    void a_miss_calls_the_model_and_stores_the_answer() {
        CachingLlmClient client = new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384);

        assertThat(client.completeJson("m", "sys", "user", SCHEMA)).isEqualTo("answer-1");

        assertThat(calls).containsExactly("user");
        assertThat(client.hits()).isZero();
        assertThat(client.misses()).isEqualTo(1);
    }

    @Test
    void a_hit_returns_the_stored_answer_without_calling_the_model_even_in_a_later_run() {
        new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384).completeJson("m", "sys", "user", SCHEMA);
        CachingLlmClient laterRun = new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384);

        assertThat(laterRun.completeJson("m", "sys", "user", SCHEMA)).isEqualTo("answer-1");

        assertThat(calls).hasSize(1);
        assertThat(laterRun.hits()).isEqualTo(1);
        assertThat(laterRun.misses()).isZero();
    }

    @Test
    void any_change_of_model_prompts_schema_or_context_size_is_a_miss() {
        CachingLlmClient client = new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384);
        client.completeJson("m", "sys", "user", SCHEMA);

        client.completeJson("other-model", "sys", "user", SCHEMA);
        client.completeJson("m", "other sys", "user", SCHEMA);
        client.completeJson("m", "sys", "other user", SCHEMA);
        client.completeJson("m", "sys", "user", OTHER_SCHEMA);
        new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 8192).completeJson("m", "sys", "user", SCHEMA);

        assertThat(calls).hasSize(6);
    }

    @Test
    void prompts_are_not_confused_when_text_moves_between_system_and_user() {
        CachingLlmClient client = new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384);

        client.completeJson("m", "ab", "c", SCHEMA);
        client.completeJson("m", "a", "bc", SCHEMA);

        assertThat(calls).hasSize(2);
    }

    @Test
    void off_neither_reads_nor_writes_the_cache() throws Exception {
        new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384).completeJson("m", "sys", "user", SCHEMA);
        CachingLlmClient off = new CachingLlmClient(model, dir, CachingLlmClient.Mode.OFF, 16384);

        assertThat(off.completeJson("m", "sys", "user", SCHEMA)).isEqualTo("answer-2");
        off.completeJson("m", "sys", "new user", SCHEMA);

        assertThat(calls).hasSize(3);
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files).hasSize(1);
        }
    }

    @Test
    void refresh_always_calls_the_model_and_overwrites_the_stored_answer() {
        new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384).completeJson("m", "sys", "user", SCHEMA);

        assertThat(new CachingLlmClient(model, dir, CachingLlmClient.Mode.REFRESH, 16384).completeJson("m", "sys", "user", SCHEMA))
                .isEqualTo("answer-2");

        assertThat(new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384).completeJson("m", "sys", "user", SCHEMA))
                .isEqualTo("answer-2");
        assertThat(calls).hasSize(2);
    }

    @Test
    void a_failed_call_is_not_cached() {
        LlmClient failing = (llmModel, system, user, schema) -> {
            throw new OllamaException("down");
        };
        CachingLlmClient client = new CachingLlmClient(failing, dir, CachingLlmClient.Mode.ON, 16384);

        assertThatThrownBy(() -> client.completeJson("m", "sys", "user", SCHEMA)).isInstanceOf(OllamaException.class);

        assertThat(new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384).completeJson("m", "sys", "user", SCHEMA))
                .isEqualTo("answer-1");
    }

    @Test
    void mode_is_parsed_case_insensitively_and_defaults_to_off() {
        assertThat(CachingLlmClient.Mode.parse(null)).isEqualTo(CachingLlmClient.Mode.OFF);
        assertThat(CachingLlmClient.Mode.parse("")).isEqualTo(CachingLlmClient.Mode.OFF);
        assertThat(CachingLlmClient.Mode.parse("On")).isEqualTo(CachingLlmClient.Mode.ON);
        assertThat(CachingLlmClient.Mode.parse("refresh")).isEqualTo(CachingLlmClient.Mode.REFRESH);
        assertThatThrownBy(() -> CachingLlmClient.Mode.parse("yes")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summary_names_mode_hits_and_misses() {
        CachingLlmClient client = new CachingLlmClient(model, dir, CachingLlmClient.Mode.ON, 16384);
        client.completeJson("m", "sys", "user", SCHEMA);
        client.completeJson("m", "sys", "user", SCHEMA);

        assertThat(client.summary()).isEqualTo("cache on: 1 hits, 1 model calls");
    }
}
