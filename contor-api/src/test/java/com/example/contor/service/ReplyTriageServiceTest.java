package com.example.contor.service;

import com.example.contor.ai.LlmClient;
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
    void triage_UnreadableAnswerMeansNoReply() {
        when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn("not json");

        assertThat(service.triage(thread()).needsReply()).isFalse();
    }
}
