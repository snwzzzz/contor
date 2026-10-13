package com.example.contor.ai.eval;

import com.example.contor.ai.EmailContent;
import com.example.contor.ai.OllamaClient;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.ProposalKind;
import com.example.contor.fieldtype.TestFieldTypes;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.service.DemoMailboxService;
import com.example.contor.service.EmailExtractionService;
import com.example.contor.service.RecordService;
import com.example.contor.service.RelationService;
import com.example.contor.service.RelationTypeService;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AiExtractionEvalTest} on the business mailbox ({@code ai-eval/business}): every labelled inbox mail (no noise,
 * no second copies) is extracted with the business schema of {@link EvalCollections#business()} and, when it depends on
 * earlier mail, with that mail as context rendered like the app does — so a terse "same as last time" is judged fairly.
 * Prints the overall report plus one line per storyline category and per edge-case tag, and compares with its own
 * baseline. Needs Ollama; {@code ./mvnw verify -Pai-eval}, about 30 minutes on an RTX 3060.
 * <p>
 * System properties as in {@link AiExtractionEvalTest}, plus {@code ai.eval.business.baseline} (default
 * {@code ../ai-eval/business-baseline.json}) and {@code ai.eval.business.limit} (only the first n mails, for quick runs).
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiBusinessExtractionEvalTest {

    private static final String DEFAULT_MODEL = "qwen3.5:9b";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Set<String> REPORTED_TAGS = Set.of("context-needed", "terse-repeat-order", "shared-driver", "different-marek", "name-typo",
            "number-typo", "calendar-week", "relative-date", "time-zone", "date-format", "forwarded", "cc-only", "several-topics", "facts-in-quote",
            "contradicting-change", "job-change", "very-short", "very-long");
    private final ObjectMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Test
    void business_extraction_quality_does_not_regress_against_its_baseline() throws IOException {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        double threshold = Double.parseDouble(System.getProperty("ai.eval.threshold", "0.05"));
        int tolerance = Integer.parseInt(System.getProperty("ai.eval.tolerance", "1"));
        int limit = Integer.parseInt(System.getProperty("ai.eval.business.limit", "100000"));
        Path baselineFile = Path.of(System.getProperty("ai.eval.business.baseline", "../ai-eval/business-baseline.json"));

        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZONE);
        OllamaClient client = new OllamaClient(properties, mapper);
        List<CollectionDto> collections = EvalCollections.business();
        Map<Long, String> slugById = new HashMap<>();
        collections.forEach(collection -> slugById.put(collection.getId(), collection.getSlug()));
        Map<Long, String> relationTypeById = new HashMap<>();
        EvalCollections.businessRelationTypes().forEach(type -> relationTypeById.put(type.getId(), type.getSlug()));
        Set<String> undirected = EvalCollections.businessRelationTypes().stream().filter(type -> !type.getDirected())
                .map(type -> type.getSlug()).collect(Collectors.toSet());

        RecordService recordService = mock(RecordService.class);
        when(recordService.listRecords(anyLong(), anyList(), isNull(), isNull(), eq(0), eq(1)))
                .thenAnswer(invocation -> new RecordPageDto(new ArrayList<>(), 0, 1, 0L, 0));
        RelationTypeService relationTypeService = mock(RelationTypeService.class);
        when(relationTypeService.findAll()).thenReturn(EvalCollections.businessRelationTypes());

        List<BusinessMessage> all = BusinessCorpus.messages();
        Map<String, BusinessMessage> byId = BusinessCorpus.byId(all);
        List<BusinessMessage> messages = all.stream().filter(BusinessMessage::labelled).limit(limit).toList();
        List<EvalCase> cases = messages.stream().map(message -> message.evalCaseWithContext(byId)).toList();

        List<CaseResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            EmailExtractionService service = new EmailExtractionService(client, properties, mapper, TestFieldTypes.registry(), recordService,
                    mock(ProposalRepository.class), relationTypeService, mock(RelationService.class), Clock.fixed(evalCase.email().receivedAt(), ZONE));
            EmailContent email = evalCase.context() != null && hasContext(evalCase.context())
                    ? evalCase.email().withContext(AiContextEvalTest.contextText(evalCase)) : evalCase.email();
            long start = System.nanoTime();
            try {
                List<ProposalDraft> drafts = service.analyze(email, collections, DemoMailboxService.OWNER_PROFILE);
                if (Boolean.getBoolean("ai.eval.verbose")) {
                    System.out.println("[ai-eval] " + evalCase.id() + " expected " + evalCase.expected().records().entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue().stream().map(EvalCase.ExpectedRecord::title).toList()).toList()
                            + "\n    produced " + drafts.stream().filter(draft -> draft.kind() != ProposalKind.CREATE_RELATION)
                            .map(draft -> AiExtractionEvalTest.produced(draft, slugById)).map(r -> r.collection() + ": " + r.title()).toList());
                }
                results.add(new CaseResult(evalCase.id(),
                        drafts.stream().filter(draft -> draft.kind() != ProposalKind.CREATE_RELATION)
                                .map(draft -> AiExtractionEvalTest.produced(draft, slugById)).toList(),
                        Duration.ofNanos(System.nanoTime() - start).toMillis(), null,
                        AiExtractionEvalTest.relations(drafts, slugById, relationTypeById)));
            } catch (RuntimeException e) {
                results.add(new CaseResult(evalCase.id(), List.of(), 0, e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }

        EvalScorer scorer = new EvalScorer(collections, undirected);
        EvalReport report = scorer.score(model, EmailExtractionService.PROMPT_VERSION + "+business", cases, results);
        writeReport(report);
        printGroups("category", scorer, model, messages, cases, results, message -> List.of(message.category().substring(0, 1)));
        printGroups("tag", scorer, model, messages, cases, results,
                message -> message.tags().stream().filter(REPORTED_TAGS::contains).toList());
        assertThat(report.failedCases()).as("model calls that failed (Ollama running? model pulled?)").isLessThan(cases.size());

        if (limit < 100000) {
            System.out.println("[ai-eval] business: limited run (" + cases.size() + " mails), not compared with the baseline");
            return;
        }
        if (Boolean.getBoolean("ai.eval.updateBaseline")) {
            Files.createDirectories(baselineFile.toAbsolutePath().getParent());
            mapper.writeValue(baselineFile, report);
            return;
        }
        if (Files.exists(baselineFile)) {
            EvalReport baseline = mapper.readValue(baselineFile, EvalReport.class);
            assertThat(BaselineComparison.regressions(report, baseline, threshold, tolerance)).as("regressions against %s", baselineFile).isEmpty();
        } else {
            System.out.println("[ai-eval] No business baseline at " + baselineFile + "; run with -Dai.eval.updateBaseline=true to create it.");
        }
    }

    private static boolean hasContext(EvalCase.Context context) {
        return !context.thread().isEmpty() || !context.fromSender().isEmpty() || !context.related().isEmpty();
    }

    /** One summary line per group (a mail can be in several groups, e.g. several tags). */
    private static void printGroups(String what, EvalScorer scorer, String model, List<BusinessMessage> messages, List<EvalCase> cases,
                                    List<CaseResult> results, Function<BusinessMessage, List<String>> groupsOf) {
        Map<String, List<EvalCase>> groups = new TreeMap<>();
        for (int i = 0; i < messages.size(); i++) {
            for (String group : groupsOf.apply(messages.get(i))) {
                groups.computeIfAbsent(group, key -> new ArrayList<>()).add(cases.get(i));
            }
        }
        groups.forEach((group, members) -> {
            EvalReport report = scorer.score(model, group, members, results);
            int expected = report.collections().values().stream().mapToInt(EvalReport.CollectionScore::expected).sum();
            int produced = report.collections().values().stream().mapToInt(EvalReport.CollectionScore::produced).sum();
            int found = report.collections().values().stream().mapToInt(EvalReport.CollectionScore::truePositives).sum();
            System.out.printf(Locale.ROOT, "[ai-eval] business %s %-20s %3d mails: records %d/%d found (%d produced), fields %d/%d%n", what, group,
                    members.size(), found, expected, produced, report.fieldsOverall().correct(), report.fieldsOverall().total());
        });
    }

    private void writeReport(EvalReport report) throws IOException {
        Path dir = Path.of("target", "ai-eval");
        Files.createDirectories(dir);
        String name = "business-report-" + report.model().replaceAll("[^A-Za-z0-9._-]", "-");
        mapper.writeValue(dir.resolve(name + ".json"), report);
        Files.writeString(dir.resolve(name + ".md"), report.toMarkdown());
        System.out.println(report.toMarkdown());
    }
}
