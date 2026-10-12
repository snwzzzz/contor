package com.example.contor.service;

import com.example.contor.ai.LlmClient;
import com.example.contor.ai.ReplyThread;
import com.example.contor.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplyComposerServiceTest {

    @Mock
    private LlmClient llmClient;

    private ReplyComposerService service(int numCtx) {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), numCtx, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        return new ReplyComposerService(llmClient, properties, JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC));
    }

    private static ReplyThread.Message message(boolean own, String subject, String body, String at) {
        return new ReplyThread.Message(own, own ? "Max Muster" : "Anna Berger", own ? "me@example.com" : "anna@acme.com",
                List.of(own ? "anna@acme.com" : "me@example.com"), subject, Instant.parse(at), body);
    }

    private static ReplyThread thread() {
        return new ReplyThread("Max Muster", "me@example.com", List.of(
                message(true, "Termin", "Hallo Anna, passt dir nächste Woche?", "2026-10-05T08:00:00Z"),
                message(false, "Re: Termin", "Hallo Max, ja gerne. Wann hättest du Zeit? Und was kostet der Workshop?",
                        "2026-10-06T08:00:00Z")));
    }

    private void answer(String json) {
        when(llmClient.completeJson(eq("extractor"), anyString(), anyString(), any())).thenReturn(json);
    }

    @Test
    void compose_ReturnsAValidatedDraft() {
        answer("""
                {"subject": "Re: Termin", "body": "Hallo Anna,\\n\\nich hätte [verfügbare Zeiten] Zeit. Der Workshop kostet [Preis].\\n\\nMax",
                 "language": "DE", "placeholders": ["verfügbare Zeiten", "[Preis]", "[nicht im Text]"], "summary": "Offers times"}""");

        Optional<ReplyComposerService.ComposedReply> reply = service(8192).compose(thread(), List.of());

        assertThat(reply).hasValueSatisfying(draft -> {
            assertThat(draft.subject()).isEqualTo("Re: Termin");
            assertThat(draft.body()).startsWith("Hallo Anna,");
            assertThat(draft.language()).isEqualTo("de");
            assertThat(draft.placeholders()).containsExactly("[verfügbare Zeiten]", "[Preis]");
            assertThat(draft.summary()).isEqualTo("Offers times");
        });
    }

    private static final ReplyComposerService.Brief SCHEDULE = new ReplyComposerService.Brief("SCHEDULE",
            List.of("Wann hättest du Zeit?", "Was kostet der Workshop?"),
            List.of("Tuesday, 13 October 2026, 11:00–12:00", "Wednesday, 14 October 2026, 09:00–10:00"),
            "Earlier emails, for context only: CONTEXT-MARKER");

    @Test
    void compose_AnswersEveryQuestionAndSaysWhichAreStillOpen() {
        answer("""
                {"subject": "Re: Termin", "body": "Hallo Anna,\\nich könnte Dienstag, 13.10., 11 Uhr oder Mittwoch, 14.10., 9 Uhr. Der Workshop kostet [Preis].\\nMax",
                 "language": "de", "placeholders": ["[Preis]"], "summary": "Offers two times",
                 "answers": [{"question": "Wann hättest du Zeit?", "answered": true}, {"question": "Was kostet der Workshop?", "answered": false}]}""");

        ReplyComposerService.ComposedReply reply = service(8192).compose(thread(), List.of(), SCHEDULE).orElseThrow();

        assertThat(reply.answers()).containsExactly(new ReplyComposerService.Answer("Wann hättest du Zeit?", true),
                new ReplyComposerService.Answer("Was kostet der Workshop?", false));
    }

    @Test
    void compose_ThePromptListsTheQuestionsTheFreeTimesAndTheContext() {
        answer("""
                {"subject": "Re: Termin", "body": "Gerne [Zeit].", "language": "de", "placeholders": [], "summary": "s", "answers": []}""");

        service(8192).compose(thread(), List.of(), SCHEDULE);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), system.capture(), prompt.capture(), any());
        assertThat(system.getValue()).contains("Answer every question").contains("only the free times");
        assertThat(prompt.getValue()).contains("What the reply has to do: SCHEDULE")
                .contains("1. Wann hättest du Zeit?").contains("2. Was kostet der Workshop?")
                .contains("- Tuesday, 13 October 2026, 11:00–12:00").contains("CONTEXT-MARKER");
        assertThat(prompt.getValue().indexOf("CONTEXT-MARKER")).isLessThan(prompt.getValue().indexOf("ANSWER THIS"));
    }

    @Test
    void compose_AnOpenQuestionWithoutAPlaceholderGivesNoDraft() {
        answer("""
                {"subject": "Re: Termin", "body": "Hallo Anna, gerne!", "language": "de", "placeholders": [], "summary": "s",
                 "answers": [{"question": "Wann hättest du Zeit?", "answered": false}]}""");

        assertThat(service(8192).compose(thread(), List.of(), SCHEDULE)).isEmpty();
    }

    @Test
    void compose_QuestionsTheModelDidNotReportOnStayUnchecked() {
        answer("""
                {"subject": "Re: Termin", "body": "Gerne am Dienstag.", "language": "de", "placeholders": [], "summary": "s"}""");

        assertThat(service(8192).compose(thread(), List.of(), SCHEDULE).orElseThrow().answers())
                .extracting(ReplyComposerService.Answer::answered).containsExactly(null, null);
    }

    @Test
    void compose_PlaceholdersInTheBodyAreListedEvenWhenTheModelForgotThem() {
        answer("""
                {"subject": "Re: Termin", "body": "Ich kann am [Tag] kommen.", "language": "de", "placeholders": [], "summary": "s"}""");

        assertThat(service(8192).compose(thread(), List.of()).orElseThrow().placeholders()).containsExactly("[Tag]");
    }

    @Test
    void compose_MissingSubjectAnswersTheOriginalOne() {
        answer("""
                {"subject": " ", "body": "Gerne!", "language": "de", "placeholders": [], "summary": "s"}""");

        assertThat(service(8192).compose(thread(), List.of()).orElseThrow().subject()).isEqualTo("Re: Termin");
    }

    @Test
    void compose_EmptyOrOverlongBodyGivesNoDraft() {
        answer("""
                {"subject": "Re: Termin", "body": "  ", "language": "de", "placeholders": [], "summary": "s"}""");
        assertThat(service(8192).compose(thread(), List.of())).isEmpty();

        answer("{\"subject\": \"Re: Termin\", \"body\": \"" + "x".repeat(ReplyComposerService.MAX_BODY_CHARS + 1)
                + "\", \"language\": \"de\", \"placeholders\": [], \"summary\": \"s\"}");
        assertThat(service(8192).compose(thread(), List.of())).isEmpty();

        answer("not json");
        assertThat(service(8192).compose(thread(), List.of())).isEmpty();
    }

    @Test
    void compose_PromptMarksTheUserAndCarriesTheGrounding() {
        answer("""
                {"subject": "Re: Termin", "body": "Gerne!", "language": "de", "placeholders": [], "summary": "s"}""");

        service(8192).compose(thread(), List.of("The sender in your contacts: Anna Berger, Trainer"));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), system.capture(), prompt.capture(), any());
        assertThat(system.getValue()).contains("2026-10-08 (Thursday)", "Europe/Vienna", "Max Muster <me@example.com>");
        assertThat(prompt.getValue())
                .contains("- The sender in your contacts: Anna Berger, Trainer")
                .contains("From: You", "passt dir nächste Woche?")
                .contains("ANSWER THIS: From: Anna Berger <anna@acme.com>");
        assertThat(prompt.getValue().indexOf("From: You")).isLessThan(prompt.getValue().indexOf("ANSWER THIS"));
    }

    @Test
    void compose_LongThreadsLeaveOutTheOldestMessages() {
        answer("""
                {"subject": "Re: Termin", "body": "Gerne!", "language": "de", "placeholders": [], "summary": "s"}""");
        List<ReplyThread.Message> messages = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            messages.add(message(day % 2 == 0, "Termin", "message " + day + " " + "y".repeat(2500), "2026-10-0" + day + "T08:00:00Z"));
        }
        messages.add(message(false, "Re: Termin", "the newest question", "2026-10-06T08:00:00Z"));

        service(2048).compose(new ReplyThread("Max Muster", "me@example.com", messages), List.of());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), anyString(), prompt.capture(), any());
        assertThat(prompt.getValue()).contains("the newest question", "earlier messages left out]").doesNotContain("message 1 ");
    }
}
