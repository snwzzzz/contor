package com.example.contor.ai.eval;

import com.example.contor.ai.OllamaClient;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.config.AiProperties;
import com.example.contor.fieldtype.TestFieldTypes;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.service.EmailExtractionService;
import com.example.contor.service.RecordService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs {@link EmailExtractionService} against the real local Ollama over the synthetic corpus (and the private one, if
 * present) with the fixed schema of {@link EvalCollections}, and scores the result. Excluded from the default build;
 * run with {@code ./mvnw verify -Pai-eval}. Each case is analyzed as if processed right when it was received (so
 * "Friday" resolves from the mail date and no to-do counts as outdated).
 * <p>
 * System properties: {@code ai.eval.model} (default: the app default), {@code ai.eval.threshold} (default 0.05),
 * {@code ai.eval.baseline} (default {@code ../ai-eval/baseline.json}), {@code ai.eval.private} (default
 * {@code ../ai-eval/private}), {@code ai.eval.updateBaseline=true} to overwrite the baseline with this run.
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiExtractionEvalTest {

    private static final String DEFAULT_MODEL = "qwen3.5:9b";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private final ObjectMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Test
    void extraction_quality_does_not_regress_against_the_baseline() throws IOException {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        double threshold = Double.parseDouble(System.getProperty("ai.eval.threshold", "0.05"));
        Path baselineFile = Path.of(System.getProperty("ai.eval.baseline", "../ai-eval/baseline.json"));
        Path privateDir = Path.of(System.getProperty("ai.eval.private", "../ai-eval/private"));

        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZONE);
        OllamaClient client = new OllamaClient(properties, mapper);

        List<EvalCase> cases = new ArrayList<>(EvalCaseLoader.loadClasspath("ai-eval/synthetic"));
        cases.addAll(EvalCaseLoader.load(privateDir));
        List<CollectionDto> collections = EvalCollections.all();
        Map<Long, String> slugById = new HashMap<>();
        collections.forEach(collection -> slugById.put(collection.getId(), collection.getSlug()));

        RecordService recordService = mock(RecordService.class);
        when(recordService.listRecords(anyLong(), anyList(), isNull(), isNull(), eq(0), eq(1)))
                .thenAnswer(invocation -> new RecordPageDto(new ArrayList<>(), 0, 1, 0L, 0));
        ProposalRepository proposalRepository = mock(ProposalRepository.class);

        List<CaseResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            EmailExtractionService service = new EmailExtractionService(client, properties, mapper, TestFieldTypes.registry(),
                    recordService, proposalRepository, Clock.fixed(evalCase.email().receivedAt(), ZONE));
            long start = System.nanoTime();
            try {
                List<ProposalDraft> drafts = service.analyze(evalCase.email(), collections);
                long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
                results.add(new CaseResult(evalCase.id(), drafts.stream().map(draft -> produced(draft, slugById)).toList(), millis, null));
            } catch (RuntimeException e) {
                results.add(new CaseResult(evalCase.id(), List.of(), 0, e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }

        EvalReport report = new EvalScorer(collections).score(model, EmailExtractionService.PROMPT_VERSION, cases, results);
        writeReports(report);
        results.stream().filter(result -> result.error() != null).limit(3)
                .forEach(result -> System.out.println("[ai-eval] " + result.caseId() + " failed: " + result.error()));
        assertThat(report.failedCases()).as("model calls that failed (Ollama running? model pulled?)").isLessThan(cases.size());

        if (Boolean.getBoolean("ai.eval.updateBaseline")) {
            Files.createDirectories(baselineFile.toAbsolutePath().getParent());
            mapper.writeValue(baselineFile, report);
            return;
        }
        if (Files.exists(baselineFile)) {
            EvalReport baseline = mapper.readValue(baselineFile, EvalReport.class);
            assertThat(BaselineComparison.regressions(report, baseline, threshold)).as("regressions against %s", baselineFile).isEmpty();
        } else {
            System.out.println("[ai-eval] No baseline at " + baselineFile + "; run with -Dai.eval.updateBaseline=true to create it.");
        }
    }

    private static ProducedRecord produced(ProposalDraft draft, Map<Long, String> slugById) {
        Map<String, Object> payload = draft.payload();
        @SuppressWarnings("unchecked")
        Map<String, Object> values = payload.get("values") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        String slug = payload.get("collectionId") instanceof Number id ? slugById.get(id.longValue()) : null;
        return new ProducedRecord(slug, String.valueOf(payload.getOrDefault("title", "")), values);
    }

    private void writeReports(EvalReport report) throws IOException {
        Path dir = Path.of("target", "ai-eval");
        Files.createDirectories(dir);
        String name = "report-" + (report.model() + "-" + report.promptVersion()).replaceAll("[^A-Za-z0-9._-]", "-");
        mapper.writeValue(dir.resolve(name + ".json"), report);
        Files.writeString(dir.resolve(name + ".md"), report.toMarkdown());
        System.out.println(report.toMarkdown());
    }
}
