package com.example.contor.service;

import com.example.contor.ai.EmailContent;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.ProposalKind;
import com.example.contor.fieldtype.TestFieldTypes;
import com.example.contor.persistence.ProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailExtractionServiceTest {

    private static final EmailContent EMAIL = new EmailContent(8L, "me@example.com", "Anna Berger", "anna@acme.com",
            List.of("me@example.com"), "Interview on Friday", Instant.parse("2026-10-06T08:00:00Z"),
            "Hi, we would like to invite you to an interview on Friday 9:00 at Acme HQ. Please send your CV by Thursday.");

    @Mock
    private LlmClient llmClient;

    @Mock
    private RecordService recordService;

    @Mock
    private ProposalRepository proposalRepository;

    private EmailExtractionService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        service = new EmailExtractionService(llmClient, properties, JsonMapper.builder().build(), TestFieldTypes.registry(),
                recordService, proposalRepository, Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC));
        lenient().when(recordService.listRecords(anyLong(), anyList(), isNull(), eq(0), eq(1)))
                .thenReturn(new RecordPageDto(new ArrayList<>(), 0, 1, 0L, 0));
    }

    private static CollectionFieldDto field(String slug, CollectionFieldType type, boolean required, Map<String, Object> options) {
        return new CollectionFieldDto(1L, slug, slug, type, new LinkedHashMap<>(options), required, false, 1).description("the " + slug);
    }

    private static CollectionDto people() {
        return new CollectionDto().id(1L).slug("people").name("People").description("People you deal with")
                .matchKeys(List.of("email"))
                .fields(List.of(field("full_name", CollectionFieldType.TEXT, true, Map.of()),
                        field("email", CollectionFieldType.EMAIL, false, Map.of()),
                        field("phone", CollectionFieldType.PHONE, false, Map.of())));
    }

    private static CollectionDto events() {
        return new CollectionDto().id(3L).slug("events").name("Calendar").description("Appointments").matchKeys(List.of())
                .fields(List.of(field("starts_at", CollectionFieldType.DATETIME, true, Map.of()),
                        field("location", CollectionFieldType.TEXT, false, Map.of())));
    }

    private static CollectionDto tasks() {
        return new CollectionDto().id(4L).slug("tasks").name("To-dos").description("Things to do").matchKeys(List.of())
                .fields(List.of(field("state", CollectionFieldType.STATUS, false, Map.of("states", List.of("open", "done"))),
                        field("due_date", CollectionFieldType.DATE, false, Map.of()),
                        field("priority", CollectionFieldType.ENUM, false, Map.of("values", List.of("low", "normal", "high")))));
    }

    private void answer(String json) {
        when(llmClient.completeJson(eq("extractor"), anyString(), anyString(), any())).thenReturn(json);
    }

    private static Map<String, Object> values(ProposalDraft draft) {
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) draft.payload().get("values");
        return values;
    }

    @Test
    void analyze_CreatesDraftsForNewRecords() {
        answer("""
                {"items": [
                  {"collection": "people", "title": "Anna Berger", "values": {"full_name": "Anna Berger", "email": "anna@acme.com"},
                   "confidence": 0.95, "reason": "Sender introduces herself"},
                  {"collection": "events", "title": "Interview at Acme", "values": {"starts_at": "2026-10-09T09:00:00+02:00", "location": "Acme HQ"},
                   "confidence": 0.9, "reason": "Interview invitation"},
                  {"collection": "tasks", "title": "Send CV to Acme", "values": {"due_date": "2026-10-08", "priority": "high", "bogus": "x"},
                   "confidence": 1.7, "reason": "Asked to send the CV"}
                ]}
                """);

        List<ProposalDraft> drafts = service.analyze(EMAIL, List.of(people(), events(), tasks()));

        assertThat(drafts).hasSize(3).allSatisfy(draft -> assertThat(draft.kind()).isEqualTo(ProposalKind.CREATE_RECORD));
        assertThat(drafts.get(0).payload()).containsEntry("collectionId", 1L).containsEntry("title", "Anna Berger");
        assertThat(values(drafts.get(0))).containsEntry("email", "anna@acme.com");
        assertThat(drafts.get(0).confidence()).isEqualByComparingTo(new BigDecimal("0.95"));
        assertThat(drafts.get(0).reasoning()).isEqualTo("Sender introduces herself");
        assertThat(values(drafts.get(1))).containsEntry("starts_at", "2026-10-09T09:00:00+02:00");
        assertThat(values(drafts.get(2))).containsOnlyKeys("due_date", "priority");
        assertThat(drafts.get(2).confidence()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void analyze_InvalidValuesDroppedAndMissingRequiredDropsTheItem() {
        answer("""
                {"items": [
                  {"collection": "events", "title": "Some event", "values": {"location": "Somewhere"}, "confidence": 0.8, "reason": "r"},
                  {"collection": "tasks", "title": "Reply", "values": {"priority": "urgent", "due_date": "next week"}, "confidence": 0.8, "reason": "r"}
                ]}
                """);

        List<ProposalDraft> drafts = service.analyze(EMAIL, List.of(events(), tasks()));

        assertThat(drafts).hasSize(1);
        assertThat(drafts.getFirst().payload()).containsEntry("collectionId", 4L).containsEntry("title", "Reply");
        assertThat(values(drafts.getFirst())).isEmpty();
    }

    @Test
    void analyze_ExistingRecordByMatchKeyBecomesUpdateWithNewValuesOnly() {
        answer("""
                {"items": [{"collection": "people", "title": "Anna Berger",
                            "values": {"full_name": "Anna B.", "email": "anna@acme.com", "phone": "+43 660 1234567"},
                            "confidence": 0.9, "reason": "Signature has a phone number"}]}
                """);
        RecordDto existing = new RecordDto(42L, 1L, new LinkedHashMap<>(Map.of("full_name", "Anna Berger", "email", "anna@acme.com")),
                new LinkedHashMap<>(), OffsetDateTime.parse("2026-01-01T00:00:00Z"), "AI").title("Anna Berger");
        when(recordService.listRecords(1L, List.of("email:eq:anna@acme.com"), null, 0, 1))
                .thenReturn(new RecordPageDto(new ArrayList<>(List.of(existing)), 0, 1, 1L, 1));

        List<ProposalDraft> drafts = service.analyze(EMAIL, List.of(people()));

        assertThat(drafts).hasSize(1);
        ProposalDraft update = drafts.getFirst();
        assertThat(update.kind()).isEqualTo(ProposalKind.UPDATE_RECORD);
        assertThat(update.payload()).containsEntry("collectionId", 1L).containsEntry("recordId", 42L).doesNotContainKey("title");
        assertThat(values(update)).containsOnly(Map.entry("phone", "+43 660 1234567"));
    }

    @Test
    void analyze_ExistingRecordWithNothingNewIsSkipped() {
        answer("""
                {"items": [{"collection": "events", "title": "Interview at Acme",
                            "values": {"starts_at": "2026-10-09T09:00:00+02:00"}, "confidence": 0.9, "reason": "r"}]}
                """);
        RecordDto existing = new RecordDto(7L, 3L, new LinkedHashMap<>(Map.of("starts_at", "2026-10-09T07:00:00Z")),
                new LinkedHashMap<>(), OffsetDateTime.parse("2026-01-01T00:00:00Z"), "AI").title("Interview at Acme");
        when(recordService.listRecords(3L, List.of("title:eq:Interview at Acme"), null, 0, 1))
                .thenReturn(new RecordPageDto(new ArrayList<>(List.of(existing)), 0, 1, 1L, 1));

        assertThat(service.analyze(EMAIL, List.of(events()))).isEmpty();
    }

    @Test
    void analyze_PendingDuplicateSkipped() {
        answer("""
                {"items": [
                  {"collection": "people", "title": "Anna Berger", "values": {"full_name": "Anna Berger", "email": "anna@acme.com"},
                   "confidence": 0.9, "reason": "r"},
                  {"collection": "tasks", "title": "Send CV to Acme", "values": {}, "confidence": 0.9, "reason": "r"}
                ]}
                """);
        when(proposalRepository.existsPendingCreateRecordWithValue(1L, "email", "anna@acme.com")).thenReturn(true);
        when(proposalRepository.existsPendingCreateRecordWithTitle(4L, "Send CV to Acme")).thenReturn(true);

        assertThat(service.analyze(EMAIL, List.of(people(), tasks()))).isEmpty();
    }

    @Test
    void analyze_UnknownCollectionIgnored() {
        answer("""
                {"items": [{"collection": "invoices", "title": "Invoice", "values": {}, "confidence": 0.9, "reason": "r"}]}
                """);

        assertThat(service.analyze(EMAIL, List.of(people()))).isEmpty();
    }

    @Test
    void analyze_PromptDescribesTheEmailAndTheCollections() {
        answer("{\"items\": []}");

        service.analyze(EMAIL, List.of(people(), events()));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), system.capture(), user.capture(), any());
        assertThat(system.getValue()).contains("2026-10-08").contains("me@example.com");
        assertThat(user.getValue())
                .contains("Interview on Friday").contains("Anna Berger <anna@acme.com>").contains("send your CV")
                .contains("events").contains("starts_at (DATETIME").contains("people").contains("full_name (TEXT, required");
    }

    @Test
    void analyze_NoTargetsNoCall() {
        assertThat(service.analyze(EMAIL, List.of())).isEmpty();
        verifyNoInteractions(llmClient);
    }

    @Test
    void analyze_MalformedAnswerYieldsNothing() {
        answer("{\"items\": \"oops\"}");

        assertThat(service.analyze(EMAIL, List.of(people()))).isEmpty();
        verify(recordService, org.mockito.Mockito.never()).listRecords(anyLong(), anyList(), any(), anyInt(), anyInt());
    }
}
