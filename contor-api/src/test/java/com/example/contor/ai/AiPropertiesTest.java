package com.example.contor.ai;

import com.example.contor.config.AiConfig;
import com.example.contor.config.AiProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AiConfig.class);

    @Test
    void defaults_to_a_local_ollama_and_one_model_for_routing_and_extraction() {
        runner.run(context -> {
            var properties = context.getBean(AiProperties.class);
            assertThat(properties.ollama().baseUrl()).isEqualTo(URI.create("http://127.0.0.1:11434"));
            assertThat(properties.ollama().timeout()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.ollama().numCtx()).isEqualTo(16384);
            assertThat(properties.ollama().keepAlive()).isEqualTo("30m");
            assertThat(properties.models().routing()).isEqualTo("qwen3.5:9b");
            assertThat(properties.models().extraction()).isEqualTo("qwen3.5:9b");
            assertThat(properties.models().embedding()).isEqualTo("nomic-embed-text");
            assertThat(properties.processing().enabled()).isTrue();
            assertThat(properties.processing().interval()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.processing().batchSize()).isEqualTo(20);
            assertThat(properties.timezone()).isEqualTo(java.time.ZoneId.of("Europe/Berlin"));
        });
    }

    @Test
    void binds_overrides() {
        runner.withPropertyValues("app.ai.ollama.base-url=http://localhost:9999", "app.ai.models.routing=small",
                        "app.ai.models.extraction=large", "app.ai.models.embedding=embed", "app.ai.ollama.num-ctx=8192",
                        "app.ai.processing.enabled=false")
                .run(context -> {
                    var properties = context.getBean(AiProperties.class);
                    assertThat(properties.ollama().baseUrl()).isEqualTo(URI.create("http://localhost:9999"));
                    assertThat(properties.ollama().numCtx()).isEqualTo(8192);
                    assertThat(properties.models().routing()).isEqualTo("small");
                    assertThat(properties.models().extraction()).isEqualTo("large");
                    assertThat(properties.models().embedding()).isEqualTo("embed");
                    assertThat(properties.processing().enabled()).isFalse();
                });
    }

    @Test
    void accepts_loopback_addresses() {
        for (String url : new String[]{"http://localhost:11434", "http://127.0.0.1:11434", "http://[::1]:11434"}) {
            assertThat(new AiProperties.Ollama(URI.create(url), Duration.ofMinutes(1), 4096, "5m").baseUrl())
                    .isEqualTo(URI.create(url));
        }
    }

    @Test
    void rejects_an_ollama_that_is_not_on_localhost() {
        for (String url : new String[]{"http://192.168.1.20:11434", "http://ollama.example.com", "http://0.0.0.0:11434"}) {
            assertThatThrownBy(() -> new AiProperties.Ollama(URI.create(url), Duration.ofMinutes(1), 4096, "5m"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("localhost");
        }
    }

    @Test
    void refuses_to_start_with_a_remote_ollama() {
        runner.withPropertyValues("app.ai.ollama.base-url=http://10.0.0.5:11434")
                .run(context -> assertThat(context).hasFailed());
    }
}
