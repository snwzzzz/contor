package com.example.contor.ai;

import com.example.contor.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Ollama's HTTP API ({@code /api/chat}, {@code /api/embed}, {@code /api/version}, {@code /api/tags}, {@code /api/pull})
 * over {@link RestClient}. Chat calls are non-streaming, deterministic (temperature 0), with thinking switched off and
 * the answer constrained to a JSON schema via {@code format}. The base URL is guaranteed to be loopback by
 * {@link AiProperties.Ollama}.
 */
@Slf4j
@Component
public class OllamaClient implements LlmClient, EmbeddingClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /** A chat answered with 5xx is tried once more: the first call after a cold start can hit a model still loading. */
    private static final int CHAT_ATTEMPTS = 2;

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OllamaClient(AiProperties properties, ObjectMapper objectMapper) {
        this(properties, restClient(properties), objectMapper);
    }

    OllamaClient(AiProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static RestClient restClient(AiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(properties.ollama().timeout());
        return RestClient.builder()
                .baseUrl(properties.ollama().baseUrl().toString())
                .requestFactory(factory)
                .build();
    }

    @Override
    public String completeJson(String model, String systemPrompt, String userPrompt, JsonNode jsonSchema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("think", false);
        body.put("keep_alive", properties.ollama().keepAlive());
        body.put("format", jsonSchema);
        body.put("options", Map.of("temperature", 0, "num_ctx", properties.ollama().numCtx()));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));

        JsonNode response = chat(body);
        JsonNode content = response.path("message").path("content");
        if (!content.isString()) {
            throw new OllamaException("Ollama returned no message content");
        }
        return content.asString();
    }

    private JsonNode chat(Map<String, Object> body) {
        for (int attempt = 1; ; attempt++) {
            try {
                return post("/api/chat", body);
            } catch (OllamaException e) {
                if (!e.isServerError() || attempt >= CHAT_ATTEMPTS) {
                    throw e;
                }
                log.info("Ollama chat failed ({}), retrying once", e.getMessage());
            }
        }
    }

    @Override
    public float[] embed(String text) {
        JsonNode response = post("/api/embed", Map.of("model", properties.models().embedding(), "input", text));
        JsonNode vector = response.path("embeddings").path(0);
        if (!vector.isArray()) {
            throw new OllamaException("Ollama returned no embedding");
        }
        float[] embedding = new float[vector.size()];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) vector.get(i).asDouble();
        }
        return embedding;
    }

    /** Ollama's version, or empty when it cannot be reached. */
    public Optional<String> version() {
        try {
            JsonNode response = restClient.get().uri("/api/version").retrieve().body(JsonNode.class);
            return response != null && response.path("version").isString()
                    ? Optional.of(response.path("version").asString())
                    : Optional.empty();
        } catch (RestClientException e) {
            log.debug("Ollama not reachable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Names of the installed models exactly as Ollama reports them (e.g. {@code nomic-embed-text:latest}). */
    public Set<String> installedModels() {
        JsonNode response = get("/api/tags");
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode model : response.path("models")) {
            names.add(model.path("name").asString());
        }
        return names;
    }

    /**
     * Downloads {@code model}, blocking until done. {@code progress} receives the percentage of the layer currently
     * downloading whenever it changes.
     */
    public void pull(String model, IntConsumer progress) {
        try {
            restClient.post().uri("/api/pull")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "stream", true))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new OllamaException(errorMessage(response.getBody().readAllBytes()));
                        }
                        readPullStream(new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8)), progress);
                        return null;
                    });
        } catch (RestClientException e) {
            throw new OllamaException("Ollama is not reachable: " + e.getMessage(), e);
        }
    }

    private void readPullStream(BufferedReader reader, IntConsumer progress) throws IOException {
        int last = -1;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode event = objectMapper.readTree(line);
            if (event.has("error")) {
                throw new OllamaException(event.path("error").asString());
            }
            long total = event.path("total").asLong(0);
            long completed = event.path("completed").asLong(0);
            if (total > 0) {
                int percent = (int) Math.min(100, completed * 100 / total);
                if (percent != last) {
                    progress.accept(percent);
                    last = percent;
                }
            }
        }
    }

    private JsonNode post(String path, Object body) {
        try {
            JsonNode response = restClient.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw new OllamaException(errorMessage(res.getBody().readAllBytes()), res.getStatusCode().is5xxServerError());
                    })
                    .body(JsonNode.class);
            return response != null ? response : objectMapper.createObjectNode();
        } catch (RestClientException e) {
            throw new OllamaException("Ollama is not reachable: " + e.getMessage(), e);
        }
    }

    private JsonNode get(String path) {
        try {
            JsonNode response = restClient.get().uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw new OllamaException(errorMessage(res.getBody().readAllBytes()), res.getStatusCode().is5xxServerError());
                    })
                    .body(JsonNode.class);
            return response != null ? response : objectMapper.createObjectNode();
        } catch (RestClientException e) {
            throw new OllamaException("Ollama is not reachable: " + e.getMessage(), e);
        }
    }

    /** Ollama answers errors as {@code {"error": "..."}}. */
    private String errorMessage(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            JsonNode error = objectMapper.readTree(text).path("error");
            return error.isString() ? "Ollama: " + error.asString() : "Ollama error: " + text;
        } catch (RuntimeException e) {
            return "Ollama error: " + text;
        }
    }
}
