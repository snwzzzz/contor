package com.example.contor.ai.eval;

import com.example.contor.ai.CachingLlmClient;
import com.example.contor.ai.EmailContent;
import com.example.contor.ai.MailContext;
import com.example.contor.ai.MailContextPrompt;
import com.example.contor.ai.OllamaClient;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.ai.ReplyThread;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.ProposalKind;
import com.example.contor.fieldtype.TestFieldTypes;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.service.EmailExtractionService;
import com.example.contor.service.RecordService;
import com.example.contor.service.RelationService;
import com.example.contor.service.RelationTypeService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Multi-mail understanding (AI v2 step 4): the cases in {@code ai-eval/context} can only be extracted fully with their
 * earlier emails. Each case runs twice, with the context (rendered exactly like the app does, {@link MailContextPrompt})
 * and without it, and both scores are printed. Report only — three cases are too few for a gate. Needs Ollama; run with
 * {@code ./mvnw verify -Pai-eval}.
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiContextEvalTest {

    private static final String DEFAULT_MODEL = "qwen3.5:9b";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int BUDGET_CHARS = 6000;

    @Test
    void earlier_emails_help_to_extract_what_an_email_refers_to() {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        JsonMapper mapper = JsonMapper.builder().build();
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZONE);
        CachingLlmClient client = CachingLlmClient.fromSystemProperties(new OllamaClient(properties, mapper), properties.ollama().numCtx());
        List<EvalCase> cases = CaseFilter.fromSystemProperty().selectOrSkip(EvalCaseLoader.loadClasspath("ai-eval/context"), EvalCase::id);
        List<CollectionDto> collections = EvalCollections.all();
        Map<Long, String> slugById = new HashMap<>();
        collections.forEach(collection -> slugById.put(collection.getId(), collection.getSlug()));

        RecordService recordService = mock(RecordService.class);
        when(recordService.listRecords(anyLong(), anyList(), isNull(), isNull(), eq(0), eq(1)))
                .thenAnswer(invocation -> new RecordPageDto(new ArrayList<>(), 0, 1, 0L, 0));
        RelationTypeService relationTypeService = mock(RelationTypeService.class);
        when(relationTypeService.findAll()).thenReturn(EvalCollections.relationTypes());

        Function<Boolean, List<CaseResult>> run = withContext -> {
            List<CaseResult> results = new ArrayList<>();
            for (EvalCase evalCase : cases) {
                EmailExtractionService service = new EmailExtractionService(client, properties, mapper, TestFieldTypes.registry(),
                        recordService, mock(ProposalRepository.class), relationTypeService, mock(RelationService.class),
                        Clock.fixed(evalCase.email().receivedAt(), ZONE));
                EmailContent email = withContext ? evalCase.email().withContext(contextText(evalCase)) : evalCase.email();
                long start = System.nanoTime();
                try {
                    List<ProposalDraft> drafts = service.analyze(email, collections);
                    List<ProducedRecord> produced = drafts.stream().filter(draft -> draft.kind() != ProposalKind.CREATE_RELATION)
                            .map(draft -> AiExtractionEvalTest.produced(draft, slugById)).toList();
                    produced.forEach(record -> System.out.println("[ai-eval] " + evalCase.id() + (withContext ? " with" : " without")
                            + " context: " + record.collection() + " – " + record.title() + " " + record.values()));
                    results.add(new CaseResult(evalCase.id(), produced, Duration.ofNanos(System.nanoTime() - start).toMillis(), null));
                } catch (RuntimeException e) {
                    results.add(new CaseResult(evalCase.id(), List.of(), 0, e.getClass().getSimpleName() + ": " + e.getMessage()));
                }
            }
            return results;
        };

        EvalScorer scorer = new EvalScorer(collections, Set.of());
        EvalReport with = scorer.score(model, EmailExtractionService.PROMPT_VERSION + "+context", cases, run.apply(true));
        EvalReport without = scorer.score(model, EmailExtractionService.PROMPT_VERSION, cases, run.apply(false));
        System.out.println("[ai-eval] context (" + cases.size() + " cases) with:    " + summary(with));
        System.out.println("[ai-eval] context (" + cases.size() + " cases) without: " + summary(without));
        System.out.println("[ai-eval] " + client.summary());
        assertThat(with.failedCases()).as("model calls that failed (Ollama running? model pulled?)").isLessThan(cases.size());
    }

    /** The case's earlier emails as the app renders them; mail from the case's mailbox is the user's own. */
    static String contextText(EvalCase evalCase) {
        EvalCase.Context context = evalCase.context() != null ? evalCase.context() : new EvalCase.Context(null, null, null, null);
        String mailbox = evalCase.email().mailbox();
        Function<EmailContent, ReplyThread.Message> message = email -> new ReplyThread.Message(
                email.fromAddress() != null && email.fromAddress().equalsIgnoreCase(mailbox), email.fromName(), email.fromAddress(),
                email.to(), email.subject(), email.receivedAt(), email.body());
        return MailContextPrompt.render(new MailContext(context.thread().stream().map(message).toList(),
                context.fromSender().stream().map(message).toList(), context.related().stream().map(message).toList(),
                context.records()), ZONE, BUDGET_CHARS);
    }

    private static String summary(EvalReport report) {
        int expected = report.collections().values().stream().mapToInt(EvalReport.CollectionScore::expected).sum();
        int produced = report.collections().values().stream().mapToInt(EvalReport.CollectionScore::produced).sum();
        int found = report.collections().values().stream().mapToInt(EvalReport.CollectionScore::truePositives).sum();
        return "records found %d/%d (%d produced), fields %d/%d, dates %d/%d".formatted(found, expected, produced,
                report.fieldsOverall().correct(), report.fieldsOverall().total(), report.dates().correct(), report.dates().total());
    }
}
