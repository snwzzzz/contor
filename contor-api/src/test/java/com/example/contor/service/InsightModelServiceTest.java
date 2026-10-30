package com.example.contor.service;

import com.example.contor.ai.DigestApiItem;
import com.example.contor.ai.DigestEmail;
import com.example.contor.ai.Insight;
import com.example.contor.ai.LlmClient;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightModelServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");

    @Mock
    private LlmClient llmClient;

    private InsightModelService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 16384, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
        service = new InsightModelService(llmClient, properties, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final List<DigestEmail> DIGEST = List.of(
            new DigestEmail(12L, "StreamBox", "billing@streambox.tv", "Your invoice October", "Total 14.99 EUR", Instant.parse("2026-10-08T08:00:00Z"), "inbox"),
            new DigestEmail(11L, "Me", "me@example.com", "Re: Contract", "Could you send the signed copy?", Instant.parse("2026-10-02T08:00:00Z"), "sent"),
            new DigestEmail(10L, "StreamBox", "billing@streambox.tv", "Your invoice September", "Total 9.99 EUR", Instant.parse("2026-09-08T08:00:00Z"), "inbox"));

    private void answer(String json) {
        when(llmClient.completeJson(eq("extractor"), anyString(), anyString(), any())).thenReturn(json);
    }

    @Test
    void findInsights_KeepsWellFoundedNonObviousInsightsWithTheirFollowUps() {
        answer("""
                {"insights": [{"title": "StreamBox got more expensive", "observation": "The October invoice is 14.99 EUR, September was 9.99 EUR.",
                  "whyItMatters": "A 50 % increase you may not have agreed to.", "evidence": [12, 10], "confidence": 0.8, "novelty": 0.7,
                  "followUps": [{"collection": "tasks", "title": "Check the StreamBox plan", "date": "2026-10-15"},
                                {"collection": "events", "title": "Call StreamBox", "date": "2026-10-16T10:00:00+02:00"}]}]}""");

        List<Insight> insights = service.findInsights(DIGEST, List.of(), List.of());

        assertThat(insights).singleElement().satisfies(insight -> {
            assertThat(insight.key()).isEqualTo("model:10,12");
            assertThat(insight.evidenceEmailIds()).containsExactly(12L, 10L);
            assertThat(insight.source()).isEqualTo("model");
            assertThat(insight.followUps()).containsExactly(
                    new Insight.FollowUp("tasks", "Check the StreamBox plan", Map.of("due_date", "2026-10-15")),
                    new Insight.FollowUp("events", "Call StreamBox", Map.of("starts_at", "2026-10-16T10:00:00+02:00")));
        });
    }

    @Test
    void findInsights_DropsWhatIsInventedObviousOrWithoutEvidence() {
        answer("""
                {"insights": [
                  {"title": "Invented", "observation": "o", "whyItMatters": "w", "evidence": [12, 99], "novelty": 0.9, "followUps": []},
                  {"title": "Obvious", "observation": "o", "whyItMatters": "w", "evidence": [12, 10], "novelty": 0.2, "followUps": []},
                  {"title": "Unfounded", "observation": "o", "whyItMatters": "w", "evidence": [], "novelty": 0.9, "followUps": []},
                  {"title": "Unsure", "observation": "o", "whyItMatters": "w", "evidence": [12], "confidence": 0.4, "novelty": 0.9, "followUps": []}]}""");

        assertThat(service.findInsights(DIGEST, List.of(), List.of())).isEmpty();
    }

    @Test
    void findInsights_SomethingMissingMayRestOnOneEmail() {
        // a promised answer that never came, a trip without accommodation: one email and an absence
        answer("""
                {"insights": [{"title": "No answer as promised", "observation": "o", "whyItMatters": "w", "evidence": [11], "novelty": 0.8,
                  "followUps": []}]}""");

        assertThat(service.findInsights(DIGEST, List.of(), List.of())).singleElement()
                .satisfies(insight -> assertThat(insight.key()).isEqualTo("model:11"));
    }

    @Test
    void findInsights_DropsFollowUpsWithoutAUsableDateForAnAppointmentAndKeepsAtMostTwo() {
        answer("""
                {"insights": [{"title": "t", "observation": "o", "whyItMatters": "w", "evidence": [12, 10], "novelty": 0.8,
                  "followUps": [{"collection": "events", "title": "Meeting", "date": "soon"},
                                {"collection": "tasks", "title": "Task without date"},
                                {"collection": "orders", "title": "Wrong collection"},
                                {"collection": "tasks", "title": "Second task", "date": "2026-10-20"},
                                {"collection": "tasks", "title": "Third task", "date": "2026-10-21"}]}]}""");

        assertThat(service.findInsights(DIGEST, List.of(), List.of()).getFirst().followUps()).containsExactly(
                new Insight.FollowUp("tasks", "Task without date", Map.of()),
                new Insight.FollowUp("tasks", "Second task", Map.of("due_date", "2026-10-20")));
    }

    @Test
    void findInsights_ThePromptListsTheDigestTheToDosAndTheAppointments() {
        answer("{\"insights\": []}");

        service.findInsights(DIGEST, List.of("Send the signed contract (due 2026-10-10)"), List.of("Dentist – 2026-10-14T09:00+02:00"));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), system.capture(), prompt.capture(), any());
        assertThat(system.getValue()).contains("Today is 2026-10-12").contains("non-obvious").contains("Never restate what a single email says");
        assertThat(system.getValue()).as("without app data the prompt stays the one for mail only").doesNotContain("App data").doesNotContain("evidenceApiItems");
        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(llmClient).completeJson(any(), any(), any(), schema.capture());
        assertThat(schema.getValue().toString()).doesNotContain("evidenceApiItems");
        assertThat(prompt.getValue()).contains("#12 ").contains("StreamBox <billing@streambox.tv>").contains("Total 14.99 EUR")
                .contains("#11 ").contains("you →").contains("Send the signed contract").contains("Dentist");
    }

    @Test
    void findInsights_NothingToReadOrAnUnreadableAnswerGivesNoInsights() {
        assertThat(service.findInsights(List.of(), List.of(), List.of())).isEmpty();
        verifyNoInteractions(llmClient);

        answer("not json");
        assertThat(service.findInsights(DIGEST, List.of(), List.of())).isEmpty();
    }

    private static final List<DigestApiItem> APP_DATA = List.of(
            new DigestApiItem(5L, "Jobs", "Saved postings", "Backend developer at Acme", "{\"id\":\"J-7\",\"title\":\"Backend developer\"}",
                    Instant.parse("2026-10-11T08:00:00Z"), true),
            new DigestApiItem(6L, "Shop", "Orders", "SO-155", "{\"id\":\"SO-155\",\"state\":\"shipped\"}",
                    Instant.parse("2026-10-10T08:00:00Z"), false));

    @Test
    void findInsights_CitesAppItemsNextToEmails() {
        answer("""
                {"insights": [{"title": "The Acme posting is gone", "observation": "The job you applied to was taken down.",
                  "whyItMatters": "The position may be filled.", "evidence": [11], "evidenceApiItems": [5], "confidence": 0.8,
                  "novelty": 0.7, "followUps": []}]}""");

        List<Insight> insights = service.findInsights(DIGEST, APP_DATA, List.of(), List.of());

        assertThat(insights).singleElement().satisfies(insight -> {
            assertThat(insight.evidenceEmailIds()).containsExactly(11L);
            assertThat(insight.evidenceApiItemIds()).containsExactly(5L);
            assertThat(insight.key()).isEqualTo("model:11|A5");
        });
    }

    @Test
    void findInsights_AnInsightMayRestOnAppItemsAlone() {
        answer("""
                {"insights": [{"title": "Order shipped", "observation": "o", "whyItMatters": "w", "evidence": [], "evidenceApiItems": [6],
                  "confidence": 0.8, "novelty": 0.7, "followUps": []},
                 {"title": "Invented", "observation": "o", "whyItMatters": "w", "evidence": [], "evidenceApiItems": [99],
                  "confidence": 0.8, "novelty": 0.7, "followUps": []},
                 {"title": "Partly invented", "observation": "o", "whyItMatters": "w", "evidence": [], "evidenceApiItems": [6, 98],
                  "confidence": 0.8, "novelty": 0.7, "followUps": []}]}""");

        assertThat(service.findInsights(List.of(), APP_DATA, List.of(), List.of())).singleElement()
                .satisfies(insight -> assertThat(insight.key()).isEqualTo("model:|A6"));
    }

    @Test
    void findInsights_ThePromptListsTheAppDataWithTokensAndSaysWhatChanged() {
        answer("{\"insights\": []}");

        service.findInsights(DIGEST, APP_DATA, List.of(), List.of());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), system.capture(), prompt.capture(), any());
        assertThat(system.getValue()).contains("App data").contains("evidenceApiItems").contains("the numbers after A");
        assertThat(prompt.getValue()).contains("Data from your apps, newest first:")
                .contains("A5 2026-10-11 · Jobs · Saved postings · gone: Backend developer at Acme {\"id\":\"J-7\"")
                .contains("A6 2026-10-10 · Shop · Orders · new: SO-155");
    }
}
