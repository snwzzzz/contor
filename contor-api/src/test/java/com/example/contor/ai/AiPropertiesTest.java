package com.example.contor.ai;

import com.example.contor.config.AiConfig;
import com.example.contor.config.AiProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AiConfig.class);

    @Test
    void defaults_to_a_local_ollama_and_default_models() {
        runner.run(context -> {
            var properties = context.getBean(AiProperties.class);
            assertThat(properties.ollama().baseUrl()).isEqualTo(URI.create("http://127.0.0.1:11434"));
            assertThat(properties.models().embedding()).isEqualTo("nomic-embed-text");
            assertThat(properties.models().routing()).isNotBlank();
            assertThat(properties.models().extraction()).isNotBlank();
        });
    }

    @Test
    void binds_overrides() {
        runner.withPropertyValues("app.ai.ollama.base-url=http://localhost:9999", "app.ai.models.routing=small",
                        "app.ai.models.extraction=large", "app.ai.models.embedding=embed")
                .run(context -> {
                    var properties = context.getBean(AiProperties.class);
                    assertThat(properties.ollama().baseUrl()).isEqualTo(URI.create("http://localhost:9999"));
                    assertThat(properties.models().routing()).isEqualTo("small");
                    assertThat(properties.models().extraction()).isEqualTo("large");
                    assertThat(properties.models().embedding()).isEqualTo("embed");
                });
    }
}
