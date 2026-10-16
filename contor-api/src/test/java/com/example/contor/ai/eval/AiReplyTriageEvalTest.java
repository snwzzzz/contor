package com.example.contor.ai.eval;

import com.example.contor.ai.CachingLlmClient;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        CachingLlmClient client = CachingLlmClient.fromSystemProperties(new OllamaClient(properties, mapper), properties.ollama().numCtx());
        ReplyTriageService triage = new ReplyTriageService(client, properties, mapper);

        List<EvalCase> cases = new ArrayList<>(EvalCaseLoader.loadClasspath("ai-eval/synthetic"));
        cases.addAll(EvalCaseLoader.loadClasspath("ai-eval/replies"));
        cases.addAll(EvalCaseLoader.loadClasspath("ai-eval/courtesy"));
        cases.addAll(EvalCaseLoader.load(privateDir));
        List<EvalCase> labelled = CaseFilter.fromSystemProperty().selectOrSkip(cases.stream().filter(evalCase -> evalCase.needsReply() != null).toList(),
                EvalCase::id);

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
        System.out.println("[ai-eval] " + client.summary());

        assertThat(failed).as("model calls that failed (Ollama running? model pulled?)").isLessThan(labelled.size());
        assertThat(score.precision()).as("reply triage precision").isGreaterThanOrEqualTo(minPrecision);
        assertThat(score.recall()).as("reply triage recall").isGreaterThanOrEqualTo(minRecall);
    }

    /**
     * The business mailbox ({@code ai-eval/business}): every labelled inbox mail with its conversation so far as the
     * thread (as the app builds it), scored for needsReply, intent (where both say a reply is needed), courtesy kinds and
     * the questions found. Report only, with the same optional precision/recall gates.
     */
    @Test
    void business_mailbox_triage_needs_reply_intent_and_questions() {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        double minPrecision = Double.parseDouble(System.getProperty("ai.eval.reply.minPrecision", "0"));
        double minRecall = Double.parseDouble(System.getProperty("ai.eval.reply.minRecall", "0"));
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
        JsonMapper mapper = JsonMapper.builder().build();
        ReplyTriageService triage = new ReplyTriageService(new OllamaClient(properties, mapper), properties, mapper);

        List<BusinessMessage> all = BusinessCorpus.messages();
        Set<String> own = all.stream().map(message -> message.email().mailbox()).collect(Collectors.toSet());
        ReplyTriageScore score = ReplyTriageScore.empty();
        int intents = 0;
        int intentsRight = 0;
        int courtesy = 0;
        int courtesyRight = 0;
        int questions = 0;
        int questionsFound = 0;
        int failed = 0;
        List<BusinessMessage> labelled = all.stream().filter(BusinessMessage::labelled).toList();
        for (BusinessMessage message : labelled) {
            try {
                EmailContent email = message.email();
                String body = EmailTextCleaner.clean(email.body(), 4000);
                var candidate = new ReplyCandidateFilter.Candidate(email.fromAddress(), Optional.ofNullable(email.to()).orElse(List.of()), body,
                        email.receivedAt(), own, false, false);
                ReplyTriageService.Triage result = ReplyCandidateFilter.skip(candidate, email.receivedAt()).isPresent() ? null
                        : triage.triage(new ReplyThread("Alex Morgan", email.mailbox(), threadOf(message, all)));
                boolean predicted = result != null && result.needsReply();
                score = score.add(message.needsReply(), predicted);
                if (predicted != message.needsReply()) {
                    System.out.println("[ai-eval] business triage wrong for " + message.id() + " (" + message.storyline() + "): expected "
                            + message.needsReply());
                }
                BusinessMessage.Reply label = message.reply();
                if (label != null && Boolean.TRUE.equals(message.needsReply()) && predicted) {
                    intents++;
                    intentsRight += label.intent() != null && label.intent().equals(result.intent()) ? 1 : 0;
                    questions += label.questions().size();
                    questionsFound += QuestionMatch.found(label.questions(), result.questions());
                }
                if (label != null && "COURTESY".equals(label.intent())) {
                    courtesy++;
                    courtesyRight += result != null && "COURTESY".equals(result.intent()) && Objects.equals(label.courtesyKind(), result.courtesyKind()) ? 1 : 0;
                }
            } catch (RuntimeException e) {
                failed++;
                System.out.println("[ai-eval] " + message.id() + " failed: " + e.getMessage());
            }
        }
        System.out.printf("[ai-eval] business triage (%s, %d mails): precision %.2f, recall %.2f, %s; intent %d/%d; courtesy %d/%d; "
                        + "questions found %d/%d%n", model, labelled.size(), score.precision(), score.recall(), score, intentsRight, intents,
                courtesyRight, courtesy, questionsFound, questions);

        assertThat(failed).as("model calls that failed (Ollama running? model pulled?)").isLessThan(labelled.size());
        assertThat(score.precision()).as("business triage precision").isGreaterThanOrEqualTo(minPrecision);
        assertThat(score.recall()).as("business triage recall").isGreaterThanOrEqualTo(minRecall);
    }

    /** The conversation up to and including {@code message}, oldest first; sent mail is the user's own. */
    static List<ReplyThread.Message> threadOf(BusinessMessage message, List<BusinessMessage> all) {
        return all.stream()
                .filter(other -> other.conversationId().equals(message.conversationId()) && other.duplicateOf() == null)
                .filter(other -> !other.email().receivedAt().isAfter(message.email().receivedAt()))
                .map(other -> new ReplyThread.Message(other.sent(), other.email().fromName(), other.email().fromAddress(), other.email().to(),
                        other.email().subject(), other.email().receivedAt(), EmailTextCleaner.clean(other.email().body(), 4000)))
                .toList();
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
