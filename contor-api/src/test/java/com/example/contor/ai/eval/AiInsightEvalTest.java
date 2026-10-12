package com.example.contor.ai.eval;

import com.example.contor.ai.DigestEmail;
import com.example.contor.ai.Insight;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.OllamaClient;
import com.example.contor.config.AiProperties;
import com.example.contor.service.InsightModelService;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The model pass of the insights (AI v2 step 8) against the local Ollama: each case in {@code ai-eval/insights} is a mail
 * digest with distractors and the evidence a good insight cites (a price change, an application gone quiet, a trip
 * without a hotel). Counts expected insights found (an insight whose evidence covers the expected emails) and how many
 * insights came out in total (noise), and prints them to be read. Report only; {@code ./mvnw verify -Pai-eval}.
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiInsightEvalTest {

    private static final String DEFAULT_MODEL = "qwen3.5:9b";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /** One scenario: what the user's mail and records looked like, and which evidence sets a good insight cites. */
    record InsightCase(String id, Instant today, List<DigestEmail> emails, List<String> openTasks, List<String> appointments,
                       List<List<Long>> expected) {
    }

    @Test
    void insights_find_what_only_shows_across_several_emails() {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZONE);
        JsonMapper mapper = JsonMapper.builder().build();
        OllamaClient ollama = new OllamaClient(properties, mapper);
        // -Dai.eval.raw=true prints the model's raw answer, to see what the guardrails dropped
        LlmClient client = (llmModel, system, user, schema) -> {
            String answer = ollama.completeJson(llmModel, system, user, schema);
            if (Boolean.getBoolean("ai.eval.raw")) {
                System.out.println("[ai-eval] raw answer: " + answer.replaceAll("\\s+", " "));
            }
            return answer;
        };

        int expected = 0;
        int found = 0;
        int produced = 0;
        for (InsightCase evalCase : load(mapper)) {
            InsightModelService service = new InsightModelService(client, properties, mapper, Clock.fixed(evalCase.today(), ZONE));
            List<Insight> insights = service.findInsights(evalCase.emails(), evalCase.openTasks(), evalCase.appointments());
            produced += insights.size();
            for (Insight insight : insights) {
                System.out.printf("[ai-eval] %s: %s %s (confidence %s)%n    %s%n    why: %s%n    follow-ups: %s%n", evalCase.id(),
                        insight.title(), insight.evidenceEmailIds(), insight.confidence(), insight.observation(), insight.whyItMatters(),
                        insight.followUps());
            }
            for (List<Long> evidence : evalCase.expected()) {
                expected++;
                boolean hit = insights.stream().anyMatch(insight -> insight.evidenceEmailIds().containsAll(evidence));
                if (hit) {
                    found++;
                }
                System.out.printf("[ai-eval] %s: expected %s %s%n", evalCase.id(), evidence, hit ? "found" : "MISSED");
            }
        }
        System.out.printf("[ai-eval] insights (%s): %d of %d expected found, %d insights in total%n", model, found, expected, produced);
        assertThat(expected).as("insight cases").isPositive();
    }

    private static List<InsightCase> load(JsonMapper mapper) {
        URL url = AiInsightEvalTest.class.getClassLoader().getResource("ai-eval/insights");
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted()
                    .map(file -> {
                        try {
                            return mapper.readValue(Files.readString(file), InsightCase.class);
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
