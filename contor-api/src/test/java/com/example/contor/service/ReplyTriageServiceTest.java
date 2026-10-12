package com.example.contor.service;

import com.example.contor.ai.LlmClient;
import com.example.contor.ai.SlotFinder;
import com.example.contor.ai.ReplyThread;
import com.example.contor.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplyTriageServiceTest {

    @Mock
    private LlmClient llmClient;

    private ReplyTriageService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        service = new ReplyTriageService(llmClient, properties, JsonMapper.builder().build());
    }

    private static ReplyThread.Message message(boolean own, String subject, String body, String at) {
        return new ReplyThread.Message(own, own ? "Me" : "Anna Berger", own ? "me@example.com" : "anna@acme.com",
                List.of(own ? "anna@acme.com" : "me@example.com"), subject, Instant.parse(at), body);
    }

    private static ReplyThread thread() {
        return new ReplyThread("Max Muster", "me@example.com", List.of(
                message(false, "Slides", "First message", "2026-10-01T08:00:00Z"),
                message(true, "Re: Slides", "My answer", "2026-10-02T08:00:00Z"),
                message(false, "Re: Slides", "Second question", "2026-10-03T08:00:00Z"),
                message(false, "Re: Slides", "Could you send me the slides by Friday?", "2026-10-06T08:00:00Z")));
    }

    @Test
    void triage_ReadsTheAnswer() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any()))
                .thenReturn("""
                        {"needsReply": true, "reason": "Anna asks for the slides", "urgency": "high"}""");

        ReplyTriageService.Triage triage = service.triage(thread());

        assertThat(triage.needsReply()).isTrue();
        assertThat(triage.reason()).isEqualTo("Anna asks for the slides");
        assertThat(triage.urgency()).isEqualTo("high");
    }

    @Test
    void triage_SendsTheEmailAndTheTwoMessagesBeforeIt() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any()))
                .thenReturn("{\"needsReply\": false, \"reason\": \"FYI\", \"urgency\": \"low\"}");

        service.triage(thread());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(llmClient).completeJson(eq("router"), anyString(), prompt.capture(), schema.capture());
        assertThat(prompt.getValue())
                .doesNotContain("First message")
                .contains("From: You", "My answer", "Second question")
                .contains("ANSWER THIS: From: Anna Berger <anna@acme.com>", "Could you send me the slides by Friday?")
                .contains("Tuesday, 2026-10-06 10:00 +02:00");
        assertThat(schema.getValue().path("properties").path("needsReply").path("type").asString()).isEqualTo("boolean");
        assertThat(schema.getValue().path("properties").path("urgency").path("enum").size()).isEqualTo(3);
    }

    @Test
    void triage_ReadsTheIntentTheQuestionsAndTheDatesAMeetingIsAskedFor() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("""
                {"needsReply": true, "reason": "Anna wants to meet", "urgency": "normal", "intent": "SCHEDULE",
                 "questions": ["When can you come next week?", " ", "Can you bring the slides?", "q3", "q4", "q5", "q6"],
                 "scheduleFrom": "2026-10-12", "scheduleTo": "2026-10-16", "timeOfDay": "EVENING"}""");

        ReplyTriageService.Triage triage = service.triage(thread());

        assertThat(triage.intent()).isEqualTo("SCHEDULE");
        assertThat(triage.timeOfDay()).isEqualTo(SlotFinder.TimeOfDay.EVENING);
        assertThat(triage.questions()).hasSize(5).startsWith("When can you come next week?", "Can you bring the slides?");
        assertThat(triage.scheduleFrom()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(triage.scheduleTo()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    void triage_AnUnknownIntentOrUnreadableDatesFallBack() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("""
                {"needsReply": true, "reason": "r", "urgency": "normal", "intent": "PANIC", "questions": ["Is Friday ok?"],
                 "scheduleFrom": "next week", "scheduleTo": "2026-10-01"}""");

        ReplyTriageService.Triage triage = service.triage(thread());

        assertThat(triage.intent()).isEqualTo("ANSWER_QUESTIONS");
        assertThat(triage.timeOfDay()).isEqualTo(SlotFinder.TimeOfDay.ANY);
        assertThat(triage.scheduleFrom()).isNull();
        assertThat(triage.scheduleTo()).isNull();
    }

    @Test
    void triage_AsksForIntentAndQuestionsAndKnowsThatBillsAndRemindersNeedNoAnswer() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("{\"needsReply\": false}");

        service.triage(thread());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(llmClient).completeJson(eq("router"), system.capture(), anyString(), schema.capture());
        assertThat(system.getValue()).contains("invoices").contains("payment reminders").contains("appointment reminders");
        assertThat(schema.getValue().path("properties").path("intent").path("enum").toString()).contains("SCHEDULE");
        assertThat(schema.getValue().path("properties").path("questions").path("type").asString()).isEqualTo("array");
    }

    @Test
    void triage_RecognizesWhereAPoliteReplyIsCustomary() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("""
                {"needsReply": false, "reason": "A rejection", "urgency": "low", "intent": "COURTESY", "courtesyKind": "REJECTION_THANKS"}""");

        ReplyTriageService.Triage triage = service.triage(thread());

        assertThat(triage.needsReply()).isFalse();
        assertThat(triage.intent()).isEqualTo("COURTESY");
        assertThat(triage.courtesyKind()).isEqualTo("REJECTION_THANKS");
    }

    @Test
    void triage_ACourtesyKindOnAMailThatNeedsNoAnswerMakesItACourtesy() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("""
                {"needsReply": false, "intent": "NONE", "courtesyKind": "THANK_YOU"}""");

        ReplyTriageService.Triage triage = service.triage(thread());

        assertThat(triage.intent()).isEqualTo("COURTESY");
        assertThat(triage.courtesyKind()).isEqualTo("THANK_YOU");
    }

    @Test
    void triage_ACourtesyNeverCountsAsNeedingAnAnswer() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("""
                {"needsReply": true, "intent": "COURTESY", "courtesyKind": "REJECTION_THANKS"}""");

        assertThat(service.triage(thread()).needsReply()).isFalse();
    }

    @Test
    void triage_ACourtesyKindWithAnotherIntentIsDropped() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("""
                {"needsReply": true, "intent": "ANSWER_QUESTIONS", "questions": ["When?"], "courtesyKind": "THANK_YOU"}""");

        ReplyTriageService.Triage triage = service.triage(thread());

        assertThat(triage.needsReply()).isTrue();
        assertThat(triage.courtesyKind()).isNull();
    }

    @Test
    void triage_AnUnknownCourtesyKindIsDropped() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("""
                {"needsReply": false, "intent": "COURTESY", "courtesyKind": "FLATTERY"}""");

        ReplyTriageService.Triage triage = service.triage(thread());

        assertThat(triage.courtesyKind()).isNull();
        assertThat(triage.intent()).isEqualTo("COURTESY");
    }

    @Test
    void triage_UnreadableAnswerMeansNoReply() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("not json");

        assertThat(service.triage(thread()).needsReply()).isFalse();
    }
}
