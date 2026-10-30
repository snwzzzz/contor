package com.example.contor.ai.eval;

import com.example.contor.ai.CachingLlmClient;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.OllamaClient;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.BaseUrl;
import com.example.contor.externalapi.DiscoveredEndpoint;
import com.example.contor.service.ApiDocsModelService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Read the docs" with the model ({@link ApiDocsModelService}, {@code api-docs-v1}) against the local Ollama: each case
 * in {@code ai-eval/api-docs} is documentation text with the paths a good answer suggests and paths it must not (write
 * endpoints, other hosts, a prompt injection). Counts expected paths found and prints every suggestion; fails only on a
 * forbidden one (the validation should already drop those). {@code ./mvnw verify -Pai-eval -Dtest=AiApiDocsEvalTest}.
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiApiDocsEvalTest {

    private static final String DEFAULT_MODEL = "qwen3.5:9b";

    record DocsCase(String id, String baseUrl, String docs, List<String> expected, List<String> forbidden) {
    }

    @Test
    void the_docs_reader_suggests_the_read_endpoints_and_nothing_else() {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
        JsonMapper mapper = JsonMapper.builder().build();
        CachingLlmClient ollama = CachingLlmClient.fromSystemProperties(new OllamaClient(properties, mapper), properties.ollama().numCtx());
        // -Dai.eval.raw=true prints the model's raw answer, to see what the validation dropped
        LlmClient client = (llmModel, system, user, schema) -> {
            String answer = ollama.completeJson(llmModel, system, user, schema);
            if (Boolean.getBoolean("ai.eval.raw")) {
                System.out.println("[ai-eval] raw answer: " + answer.replaceAll("\\s+", " "));
            }
            return answer;
        };
        ApiDocsModelService service = new ApiDocsModelService(client, properties, mapper);

        int expected = 0;
        int found = 0;
        int suggested = 0;
        int forbidden = 0;
        for (DocsCase docsCase : CaseFilter.fromSystemProperty().selectOrSkip(load(mapper), DocsCase::id)) {
            List<DiscoveredEndpoint> endpoints = service.readDocs(docsCase.docs(), new BaseUrl(docsCase.baseUrl()));
            List<String> paths = endpoints.stream().map(endpoint -> endpoint.path().value()).toList();
            suggested += endpoints.size();
            for (DiscoveredEndpoint endpoint : endpoints) {
                System.out.printf("[ai-eval] %s: %s %s%s items %s key %s%n", docsCase.id(), endpoint.name(), endpoint.path().value(),
                        endpoint.query().isEmpty() ? "" : " " + endpoint.query(), endpoint.itemsPath(), endpoint.itemKeyPath());
            }
            for (String path : docsCase.expected()) {
                expected++;
                boolean hit = paths.contains(path);
                if (hit) found++;
                System.out.printf("[ai-eval] %s: expected %s %s%n", docsCase.id(), path, hit ? "found" : "MISSED");
            }
            for (String path : docsCase.forbidden()) {
                if (paths.contains(path)) {
                    forbidden++;
                    System.out.printf("[ai-eval] %s: FORBIDDEN %s suggested%n", docsCase.id(), path);
                }
            }
        }
        System.out.printf("[ai-eval] api docs (%s, %s): %d of %d expected found, %d suggestions, %d forbidden%n", model,
                ApiDocsModelService.PROMPT_VERSION, found, expected, suggested, forbidden);
        System.out.println("[ai-eval] " + ollama.summary());
        assertThat(expected).as("api docs cases").isPositive();
        assertThat(forbidden).as("forbidden suggestions").isZero();
    }

    private static List<DocsCase> load(JsonMapper mapper) {
        URL url = AiApiDocsEvalTest.class.getClassLoader().getResource("ai-eval/api-docs");
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted()
                    .map(file -> {
                        try {
                            return mapper.readValue(Files.readString(file), DocsCase.class);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
