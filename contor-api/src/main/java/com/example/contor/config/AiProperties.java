package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;

/**
 * Local AI settings ({@code app.ai.*}). Ollama runs natively on the host and must stay bound to localhost.
 *
 * @param models routing: small fast model that decides what an email is about; extraction: larger model that fills
 *               proposals; embedding: embedding model (768 dimensions expected)
 */
@ConfigurationProperties("app.ai")
public record AiProperties(@DefaultValue Ollama ollama, @DefaultValue Models models) {

    public record Ollama(@DefaultValue("http://127.0.0.1:11434") URI baseUrl) {
    }

    public record Models(
            @DefaultValue("qwen3:4b") String routing,
            @DefaultValue("qwen3:14b") String extraction,
            @DefaultValue("nomic-embed-text") String embedding) {
    }
}
