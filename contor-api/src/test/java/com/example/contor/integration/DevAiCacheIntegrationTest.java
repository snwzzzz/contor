package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.ai.CachingLlmClient;
import com.example.contor.ai.LlmClient;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev AI cache in the real app (embedded Tomcat): the default relative folder {@code ../.ai-cache} must bind. Bound
 * as a {@code Path}, Spring resolved it through the servlet context and refused to start.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.ai.cache.mode=on", "app.ai.processing.enabled=false"})
@ActiveProfiles("dev")
@Import(PostgresTestcontainersConfiguration.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DevAiCacheIntegrationTest {

    @Autowired
    LlmClient llmClient;

    @Test
    void the_dev_app_starts_with_the_cache_in_the_default_folder() {
        assertThat(llmClient).isInstanceOf(CachingLlmClient.class);
        assertThat(((CachingLlmClient) llmClient).directory()).isEqualTo(Path.of(CachingLlmClient.DEFAULT_DIRECTORY));
    }
}
