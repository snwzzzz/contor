package com.example.contor.config;

import com.example.contor.ai.CachingLlmClient;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.OllamaClient;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DevAiCacheConfigTest {

    private static final AiProperties AI = new AiProperties(
            new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
            new AiProperties.Models("m", "m", "e"), new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZoneId.of("UTC"));

    @TempDir
    Path dir;

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(DevAiCacheConfig.class)
            .withBean(OllamaClient.class, () -> mock(OllamaClient.class))
            .withBean(AiProperties.class, () -> AI);

    private WebApplicationContextRunner dev() {
        return runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"));
    }

    @Test
    void in_dev_with_the_cache_on_services_get_the_caching_client() {
        dev().withPropertyValues("app.ai.cache.mode=on", "app.ai.cache.directory=" + dir).run(context -> {
            LlmClient client = context.getBean(LlmClient.class);
            assertThat(client).isInstanceOf(CachingLlmClient.class);
            assertThat(((CachingLlmClient) client).mode()).isEqualTo(CachingLlmClient.Mode.ON);
            assertThat(((CachingLlmClient) client).directory()).isEqualTo(dir);
        });
    }

    @Test
    void a_relative_directory_like_the_default_is_a_plain_file_system_path() {
        dev().withPropertyValues("app.ai.cache.mode=on", "app.ai.cache.directory=../.ai-cache").run(context ->
                assertThat(context.getBean(CachingLlmClient.class).directory()).isEqualTo(Path.of("../.ai-cache")));
        dev().withPropertyValues("app.ai.cache.mode=on").run(context ->
                assertThat(context.getBean(CachingLlmClient.class).directory()).isEqualTo(Path.of(CachingLlmClient.DEFAULT_DIRECTORY)));
    }

    @Test
    void in_dev_without_a_mode_the_cache_is_off() {
        dev().run(context -> assertThat(context.getBean(LlmClient.class)).isInstanceOf(OllamaClient.class));
    }

    @Test
    void in_dev_with_the_cache_off_services_talk_to_ollama_directly() {
        dev().withPropertyValues("app.ai.cache.mode=off").run(context ->
                assertThat(context.getBean(LlmClient.class)).isInstanceOf(OllamaClient.class));
    }

    @Test
    void outside_dev_the_cache_never_exists_even_when_switched_on() {
        runner.withPropertyValues("app.ai.cache.mode=on", "app.ai.cache.directory=" + dir).run(context -> {
            assertThat(context).doesNotHaveBean(CachingLlmClient.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(OllamaClient.class);
        });
    }

    @Test
    void an_unknown_mode_stops_the_start() {
        dev().withPropertyValues("app.ai.cache.mode=maybe").run(context -> assertThat(context).hasFailed());
    }
}
