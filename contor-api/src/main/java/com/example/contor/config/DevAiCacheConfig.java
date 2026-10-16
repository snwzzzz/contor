package com.example.contor.config;

import com.example.contor.ai.CachingLlmClient;
import com.example.contor.ai.OllamaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Dev profile only: with {@code AI_CACHE=on} every service gets the {@link CachingLlmClient} instead of talking to
 * {@link OllamaClient} directly, so re-analyzing mail with unchanged prompts is answered from disk. Off (the default),
 * no bean exists and nothing changes. Embeddings are not cached.
 */
@Slf4j
@Profile("dev")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DevAiCacheProperties.class)
public class DevAiCacheConfig {

    @Bean
    @Primary
    @ConditionalOnExpression("!'${app.ai.cache.mode:off}'.trim().equalsIgnoreCase('off')")
    public CachingLlmClient cachingLlmClient(OllamaClient ollama, AiProperties ai, DevAiCacheProperties cache) {
        CachingLlmClient client = new CachingLlmClient(ollama, cache.directoryPath(), CachingLlmClient.Mode.parse(cache.mode()), ai.ollama().numCtx());
        log.warn("AI answer cache {} in {} (dev only): unchanged prompts are answered from disk, not by the model",
                client.mode(), client.directory().toAbsolutePath().normalize());
        return client;
    }
}
