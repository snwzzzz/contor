package com.example.contor.ai.eval;

import com.example.contor.ai.EmailContent;
import com.example.contor.ai.EmailTextCleaner;
import com.example.contor.ai.OllamaClient;
import com.example.contor.ai.ReplyCandidateFilter;
import com.example.contor.ai.ReplyThread;
import com.example.contor.config.AiProperties;
import com.example.contor.service.ReplyTriageService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scores which emails get a reply draft (candidate rules, then the triage call against the real local Ollama) on every
 * case labelled with {@code needsReply}: the synthetic extraction corpus, {@code ai-eval/replies} and the private
 * corpus. The drafts themselves are judged by reading them; there is no automatic text score. Excluded from the
 * default build; run with {@code ./mvnw verify -Pai-eval}.
 * <p>
 * System properties: {@code ai.eval.model}, {@code ai.eval.private} (as in {@link AiExtractionEvalTest}),
 * {@code ai.eval.reply.minPrecision} and {@code ai.eval.reply.minRecall} (default 0: report only).
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiReplyTriageEvalTest {

    private static final String DEFAULT_MODEL = "qwen3.5:9b";

    @Test
    void reply_triage_precision_and_recall() {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        Path privateDir = Path.of(System.getProperty("ai.eval.private", "../ai-eval/private"));
        double minPrecision = Double.parseDouble(System.getProperty("ai.eval.reply.minPrecision", "0"));
        double minRecall = Double.parseDouble(System.getProperty("ai.eval.reply.minRecall", "0"));

        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
        JsonMapper mapper = JsonMapper.builder().build();
        ReplyTriageService triage = new ReplyTriageService(new OllamaClient(properties, mapper), properties, mapper);

        List<EvalCase> cases = new ArrayList<>(EvalCaseLoader.loadClasspath("ai-eval/synthetic"));
        cases.addAll(EvalCaseLoader.loadClasspath("ai-eval/replies"));
        cases.addAll(EvalCaseLoader.load(privateDir));
        List<EvalCase> labelled = cases.stream().filter(evalCase -> evalCase.needsReply() != null).toList();

        ReplyTriageScore score = ReplyTriageScore.empty();
        int failed = 0;
        for (EvalCase evalCase : labelled) {
            try {
                boolean predicted = decide(evalCase.email(), triage);
                score = score.add(evalCase.needsReply(), predicted);
                if (predicted != evalCase.needsReply()) {
                    System.out.println("[ai-eval] reply triage wrong for " + evalCase.id() + ": expected " + evalCase.needsReply());
                }
            } catch (RuntimeException e) {
                failed++;
                System.out.println("[ai-eval] " + evalCase.id() + " failed: " + e.getMessage());
            }
        }
        System.out.printf("[ai-eval] reply triage (%s, %d cases): precision %.2f, recall %.2f, %s%n", model, labelled.size(),
                score.precision(), score.recall(), score);

        assertThat(failed).as("model calls that failed (Ollama running? model pulled?)").isLessThan(labelled.size());
        assertThat(score.precision()).as("reply triage precision").isGreaterThanOrEqualTo(minPrecision);
        assertThat(score.recall()).as("reply triage recall").isGreaterThanOrEqualTo(minRecall);
    }

    /** What the pipeline decides for a single email: the rules first (as if it just arrived), then the triage call. */
    private static boolean decide(EmailContent email, ReplyTriageService triage) {
        String body = EmailTextCleaner.clean(email.body(), 4000);
        Set<String> own = email.mailbox() != null ? Set.of(email.mailbox()) : Set.of();
        var candidate = new ReplyCandidateFilter.Candidate(email.fromAddress(), Optional.ofNullable(email.to()).orElse(List.of()),
                body, email.receivedAt(), own, false, false);
        if (ReplyCandidateFilter.skip(candidate, email.receivedAt()).isPresent()) {
            return false;
        }
        ReplyThread thread = new ReplyThread("Alex Morgan", email.mailbox(), List.of(new ReplyThread.Message(false, email.fromName(),
                email.fromAddress(), email.to(), email.subject(), email.receivedAt(), body)));
        return triage.triage(thread).needsReply();
    }
}
