package com.example.contor.ai.eval;

import com.example.contor.ai.EmailContent;
import com.example.contor.ai.OllamaClient;
import com.example.contor.ai.ReplyCandidateFilter;
import com.example.contor.ai.ReplyThread;
import com.example.contor.ai.SlotFinder;
import com.example.contor.config.AiProperties;
import com.example.contor.service.ReplyComposerService;
import com.example.contor.service.ReplyTriageService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reply drafts end to end against the local Ollama (AI v2 step 6): triage (intent, questions, asked dates), free times
 * from an empty calendar inside office hours, then the reply-v2 draft. Prints every draft to be read, and counts what
 * can be checked: drafts written, questions found and answered, and whether a scheduling draft names an offered day.
 * Report only. Runs the {@code ai-eval/replies} cases that need a reply; {@code ./mvnw verify -Pai-eval}.
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiReplyDraftEvalTest {

    private static final String DEFAULT_MODEL = "qwen3.5:9b";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final SlotFinder.WorkingHours OFFICE = new SlotFinder.WorkingHours(
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(18, 0));

    @Test
    void drafts_answer_the_questions_and_offer_free_times() {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZONE);
        JsonMapper mapper = JsonMapper.builder().build();
        OllamaClient client = new OllamaClient(properties, mapper);
        ReplyTriageService triageService = new ReplyTriageService(client, properties, mapper);
        List<EvalCase> cases = EvalCaseLoader.loadClasspath("ai-eval/replies").stream()
                .filter(evalCase -> Boolean.TRUE.equals(evalCase.needsReply())).toList();

        int drafts = 0;
        int questions = 0;
        int answered = 0;
        int open = 0;
        int scheduling = 0;
        int schedulingNamingADay = 0;
        for (EvalCase evalCase : cases) {
            EmailContent email = evalCase.email();
            ReplyThread thread = new ReplyThread("Alex Morgan", email.mailbox(), List.of(new ReplyThread.Message(false, email.fromName(),
                    email.fromAddress(), email.to(), email.subject(), email.receivedAt(), email.body())));
            ReplyTriageService.Triage triage = triageService.triage(thread);
            List<SlotFinder.Slot> slots = List.of();
            if (triage.asksForTimes()) {
                LocalDate today = email.receivedAt().atZone(ZONE).toLocalDate();
                LocalDate from = triage.scheduleFrom() != null ? triage.scheduleFrom() : today;
                LocalDate to = triage.scheduleTo() != null ? triage.scheduleTo() : from.plusDays(10);
                slots = SlotFinder.freeSlots(List.of(), triage.timeOfDay().narrow(OFFICE), from, to, Duration.ofHours(1), email.receivedAt(), ZONE, 3);
            }
            ReplyComposerService composer = new ReplyComposerService(client, properties, mapper, Clock.fixed(email.receivedAt(), ZONE));
            Optional<ReplyComposerService.ComposedReply> reply = composer.compose(thread, List.of(),
                    new ReplyComposerService.Brief(triage.intent(), triage.questions(), slots.stream().map(SlotFinder.Slot::describe).toList(), ""));

            System.out.printf("[ai-eval] %s: intent %s, questions %s, from %s to %s (%s), offered %s%n", evalCase.id(), triage.intent(),
                    triage.questions(), triage.scheduleFrom(), triage.scheduleTo(), triage.timeOfDay(),
                    slots.stream().map(SlotFinder.Slot::describe).toList());
            questions += triage.questions().size();
            if (reply.isEmpty()) {
                System.out.println("[ai-eval]   no draft (unusable or an open question without a placeholder)");
                continue;
            }
            drafts++;
            answered += (int) reply.get().answers().stream().filter(answer -> Boolean.TRUE.equals(answer.answered())).count();
            open += (int) reply.get().answers().stream().filter(answer -> Boolean.FALSE.equals(answer.answered())).count();
            if (!slots.isEmpty()) {
                scheduling++;
                String body = reply.get().body();
                if (slots.stream().anyMatch(slot -> body.contains(String.valueOf(slot.start().getDayOfMonth()))
                        || body.contains(slot.start().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)))) {
                    schedulingNamingADay++;
                }
            }
            System.out.println("[ai-eval]   answers " + reply.get().answers() + "\n" + reply.get().body().indent(4));
        }
        System.out.printf("[ai-eval] reply drafts (%s, %d cases): %d drafts, %d questions found, %d answered, %d left open, "
                + "%d of %d scheduling drafts name an offered day%n", model, cases.size(), drafts, questions, answered, open,
                schedulingNamingADay, scheduling);
        assertThat(drafts).as("drafts written (Ollama running? model pulled?)").isPositive();
    }

    /** {@code ai-eval/courtesy}: personal mail that needs no answer but deserves one; automated senders must get none. */
    @Test
    void polite_replies_for_rejections_thanks_and_good_news() {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZONE);
        JsonMapper mapper = JsonMapper.builder().build();
        OllamaClient client = new OllamaClient(properties, mapper);
        ReplyTriageService triageService = new ReplyTriageService(client, properties, mapper);

        int personal = 0;
        int recognized = 0;
        int drafts = 0;
        int automatedWithoutDraft = 0;
        for (EvalCase evalCase : EvalCaseLoader.loadClasspath("ai-eval/courtesy")) {
            EmailContent email = evalCase.email();
            if (ReplyCandidateFilter.isAutomatedSender(email.fromAddress())) {
                automatedWithoutDraft++;
                System.out.println("[ai-eval] " + evalCase.id() + ": automated sender, no draft (rules)");
                continue;
            }
            personal++;
            ReplyThread thread = new ReplyThread("Alex Morgan", email.mailbox(), List.of(new ReplyThread.Message(false, email.fromName(),
                    email.fromAddress(), email.to(), email.subject(), email.receivedAt(), email.body())));
            ReplyTriageService.Triage triage = triageService.triage(thread);
            System.out.printf("[ai-eval] %s: needsReply %s, intent %s, kind %s%n", evalCase.id(), triage.needsReply(), triage.intent(),
                    triage.courtesyKind());
            if (!"COURTESY".equals(triage.intent())) {
                continue;
            }
            recognized++;
            Optional<ReplyComposerService.ComposedReply> reply = new ReplyComposerService(client, properties, mapper,
                    Clock.fixed(email.receivedAt(), ZONE)).compose(thread, List.of(),
                    new ReplyComposerService.Brief("COURTESY", List.of(), List.of(), "", triage.courtesyKind()));
            if (reply.isPresent()) {
                drafts++;
                System.out.println(reply.get().body().indent(4));
            }
        }
        System.out.printf("[ai-eval] courtesy (%s): %d of %d personal mails recognized, %d drafts, %d automated without draft%n",
                model, recognized, personal, drafts, automatedWithoutDraft);
        assertThat(personal).as("personal courtesy cases").isPositive();
    }
}
