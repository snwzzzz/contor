package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NoopLlmClientTest {

    @Test
    void answers_every_prompt_with_an_empty_json_object() {
        var client = new NoopLlmClient();

        assertThat(client.completeJson("system", "user", JsonMapper.builder().build().createObjectNode())).isEqualTo("{}");
    }
}
