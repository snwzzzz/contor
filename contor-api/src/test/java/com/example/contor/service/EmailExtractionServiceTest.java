package com.example.contor.service;

import com.example.contor.ai.EmailContent;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.Creator;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.RelationTypeDto;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
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

    @Mock
    private RelationTypeService relationTypeService;

    @Mock
    private RelationService relationService;

    private EmailExtractionService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        service = new EmailExtractionService(llmClient, properties, JsonMapper.builder().build(), TestFieldTypes.registry(),
                recordService, proposalRepository, relationTypeService, relationService, Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC));
        lenient().when(recordService.listRecords(anyLong(), anyList(), isNull(), isNull(), eq(0), eq(1)))
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
        when(recordService.listRecords(1L, List.of("email:eq:anna@acme.com"), null, null, 0, 1))
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
        when(recordService.listRecords(3L, List.of("title:eq:Interview at Acme"), null, null, 0, 1))
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
        when(proposalRepository.findPendingCreateRecordIdWithValue(1L, "email", "anna@acme.com")).thenReturn(Optional.of(11L));
        when(proposalRepository.findPendingCreateRecordIdWithTitle(4L, "Send CV to Acme")).thenReturn(Optional.of(12L));

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
    void analyze_EarlierEmailsComeBetweenTheCollectionsAndTheEmailOnlyInTheExtractionCall() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        answerWith(PERSON + "," + ORGANIZATION, WORKS_AT);
        EmailContent withContext = EMAIL.withContext("Earlier emails, for context only: CONTEXT-MARKER");

        service.analyze(withContext, List.of(people(), organizations()));

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(2)).completeJson(eq("extractor"), anyString(), user.capture(), any());
        String extraction = user.getAllValues().get(0);
        assertThat(extraction.indexOf("Collections:")).isLessThan(extraction.indexOf("CONTEXT-MARKER"));
        assertThat(extraction.indexOf("CONTEXT-MARKER")).isLessThan(extraction.indexOf("Email:\nFrom:"));
        assertThat(user.getAllValues().get(1)).doesNotContain("CONTEXT-MARKER");
    }

    @Test
    void analyze_WithoutContextThePromptHasNoEarlierEmails() {
        answer("{\"items\": []}");

        service.analyze(EMAIL, List.of(people()));

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), anyString(), user.capture(), any());
        assertThat(user.getValue()).doesNotContain("Earlier emails");
    }

    @Test
    void analyze_ToldToResolveRelativeDatesFromTheEmailDate() {
        answer("{\"items\": []}");

        service.analyze(EMAIL, List.of(events()));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), system.capture(), user.capture(), any());
        assertThat(system.getValue()).contains("Today is 2026-10-08 (Thursday)")
                .contains("relative to the date the email was sent");
        assertThat(user.getValue()).contains("Date: Tuesday, 2026-10-06T10:00:00+02:00");
    }

    @Test
    void analyze_DropsToDosWhoseDeadlinePassedButKeepsPastEvents() {
        answer("""
                {"items": [
                  {"collection": "tasks", "title": "Send CV", "values": {"due_date": "2026-09-01"}, "confidence": 0.9, "reason": "r"},
                  {"collection": "tasks", "title": "Sign contract", "values": {"due_date": "2026-10-08"}, "confidence": 0.9, "reason": "r"},
                  {"collection": "tasks", "title": "Call back", "values": {}, "confidence": 0.9, "reason": "r"},
                  {"collection": "events", "title": "Old interview", "values": {"starts_at": "2026-09-01T09:00:00+02:00"}, "confidence": 0.9, "reason": "r"}
                ]}
                """);

        List<ProposalDraft> drafts = service.analyze(EMAIL, List.of(tasks(), events()));

        assertThat(drafts).extracting(draft -> draft.payload().get("title")).containsExactly("Sign contract", "Call back", "Old interview");
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
        verify(recordService, org.mockito.Mockito.never()).listRecords(anyLong(), anyList(), any(), any(), anyInt(), anyInt());
    }

    // ---- relations -------------------------------------------------------------------------------------------

    private static CollectionDto organizations() {
        return new CollectionDto().id(2L).slug("organizations").name("Organizations").description("Companies").matchKeys(List.of("domain"))
                .fields(List.of(field("name", CollectionFieldType.TEXT, true, Map.of()),
                        field("domain", CollectionFieldType.TEXT, false, Map.of())));
    }

    private static RelationTypeDto worksAt() {
        return new RelationTypeDto(10L, "works_at", "works at", true, new LinkedHashMap<>(), Creator.USER)
                .inverseName("employs").fromCollectionId(1L).toCollectionId(2L);
    }

    private static final String PERSON = """
            {"collection": "people", "title": "Anna Berger",
             "values": {"full_name": "Anna Berger", "email": "anna@acme.com"}, "confidence": 0.95, "reason": "r"}""";
    private static final String ORGANIZATION = """
            {"collection": "organizations", "title": "Acme", "values": {"name": "Acme", "domain": "acme.com"},
             "confidence": 0.9, "reason": "r"}""";
    private static final String WORKS_AT = """
            {"type": "works_at", "from": "r1", "to": "r2", "confidence": 0.8, "reason": "Signature"}""";

    /** The extraction call answers with the items, a second call (only for 2+ items) with the relations between them. */
    private void answerWith(String items, String relations) {
        when(llmClient.completeJson(eq("extractor"), anyString(), anyString(), any()))
                .thenReturn("{\"items\": [" + items + "]}", "{\"relations\": [" + relations + "]}");
    }

    private static RecordPageDto page(RecordDto... records) {
        return new RecordPageDto(new ArrayList<>(List.of(records)), 0, 1, (long) records.length, 1);
    }

    private static RecordDto record(long id, long collectionId, Map<String, Object> values) {
        return new RecordDto(id, collectionId, new LinkedHashMap<>(values), new LinkedHashMap<>(), OffsetDateTime.parse("2026-01-01T00:00:00Z"), "AI");
    }

    private static List<ProposalDraft> relations(List<ProposalDraft> drafts) {
        return drafts.stream().filter(draft -> draft.kind() == ProposalKind.CREATE_RELATION).toList();
    }

    @Test
    void analyze_RelationBetweenNewRecordsDependsOnTheirDrafts() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        answerWith(PERSON + "," + ORGANIZATION, WORKS_AT);

        List<ProposalDraft> drafts = service.analyze(EMAIL, List.of(people(), organizations()));

        assertThat(drafts).hasSize(3);
        assertThat(drafts.get(0).ref()).isEqualTo("r1");
        assertThat(drafts.get(1).ref()).isEqualTo("r2");
        ProposalDraft relation = drafts.get(2);
        assertThat(relation.kind()).isEqualTo(ProposalKind.CREATE_RELATION);
        assertThat(relation.payload()).containsEntry("relationTypeId", 10L)
                .containsEntry("from", Map.of("ref", "r1")).containsEntry("to", Map.of("ref", "r2"));
        assertThat(relation.dependsOnRefs()).containsExactly("r1", "r2");
        assertThat(relation.dependsOnProposalIds()).isEmpty();
        assertThat(relation.confidence()).isEqualByComparingTo(new BigDecimal("0.8"));
        assertThat(relation.reasoning()).isEqualTo("Signature");
    }

    @Test
    void analyze_RelationToAnExistingRecordUsesItsIdWithoutADependency() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        when(recordService.listRecords(1L, List.of("email:eq:anna@acme.com"), null, null, 0, 1))
                .thenReturn(page(record(77L, 1L, Map.of("full_name", "Anna Berger", "email", "anna@acme.com"))));
        answerWith(PERSON + "," + ORGANIZATION, WORKS_AT);

        List<ProposalDraft> drafts = service.analyze(EMAIL, List.of(people(), organizations()));

        assertThat(drafts).hasSize(2);
        ProposalDraft relation = relations(drafts).getFirst();
        assertThat(relation.payload()).containsEntry("from", Map.of("collectionId", 1L, "recordId", 77L))
                .containsEntry("to", Map.of("ref", "r2"));
        assertThat(relation.dependsOnRefs()).containsExactly("r2");
    }

    @Test
    void analyze_RelationToARecordWithAPendingProposalDependsOnThatProposal() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        lenient().when(proposalRepository.findPendingCreateRecordIdWithValue(2L, "domain", "acme.com")).thenReturn(Optional.of(55L));
        answerWith(PERSON + "," + ORGANIZATION, WORKS_AT);

        List<ProposalDraft> drafts = service.analyze(EMAIL, List.of(people(), organizations()));

        assertThat(drafts).hasSize(2);
        ProposalDraft relation = relations(drafts).getFirst();
        assertThat(relation.payload()).containsEntry("to", Map.of("proposalId", 55L));
        assertThat(relation.dependsOnProposalIds()).containsExactly(55L);
        assertThat(relation.dependsOnRefs()).containsExactly("r1");
    }

    @Test
    void analyze_RelationThatAlreadyExistsIsDropped() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        when(recordService.listRecords(1L, List.of("email:eq:anna@acme.com"), null, null, 0, 1))
                .thenReturn(page(record(77L, 1L, Map.of("full_name", "Anna Berger", "email", "anna@acme.com"))));
        when(recordService.listRecords(2L, List.of("domain:eq:acme.com"), null, null, 0, 1))
                .thenReturn(page(record(88L, 2L, Map.of("name", "Acme", "domain", "acme.com"))));
        when(relationService.listRelations(1L, 77L)).thenReturn(List.of(new RelationDto().relationTypeId(10L)
                .fromCollectionId(1L).fromRecordId(77L).toCollectionId(2L).toRecordId(88L)));
        answerWith(PERSON + "," + ORGANIZATION, WORKS_AT);

        assertThat(service.analyze(EMAIL, List.of(people(), organizations()))).isEmpty();
    }

    @Test
    void analyze_SelfRelationsAreDropped() {
        when(relationTypeService.findAll()).thenReturn(List.of(
                new RelationTypeDto(11L, "knows", "knows", false, new LinkedHashMap<>(), Creator.USER).fromCollectionId(1L).toCollectionId(1L)));
        when(recordService.listRecords(1L, List.of("email:eq:anna@acme.com"), null, null, 0, 1))
                .thenReturn(page(record(77L, 1L, Map.of("full_name", "Anna Berger", "email", "anna@acme.com"))));
        String second = PERSON.replace("Anna Berger", "A. Berger");
        answerWith(PERSON + "," + second,
                "{\"type\": \"knows\", \"from\": \"r1\", \"to\": \"r1\", \"confidence\": 0.9, \"reason\": \"r\"},"
                        + "{\"type\": \"knows\", \"from\": \"r1\", \"to\": \"r2\", \"confidence\": 0.9, \"reason\": \"r\"}");

        assertThat(relations(service.analyze(EMAIL, List.of(people())))).isEmpty();
    }

    @Test
    void analyze_InvalidRelationsAreDropped() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        String dropped = "{\"collection\": \"organizations\", \"title\": \"No name\", \"values\": {}, \"confidence\": 0.9, \"reason\": \"r\"}";
        answerWith(PERSON + "," + ORGANIZATION + "," + dropped,
                // wrong direction for a directed type
                "{\"type\": \"works_at\", \"from\": \"r2\", \"to\": \"r1\", \"confidence\": 0.9, \"reason\": \"r\"},"
                        // unknown type, unknown ref, a ref whose item was dropped
                        + "{\"type\": \"friends\", \"from\": \"r1\", \"to\": \"r2\", \"confidence\": 0.9, \"reason\": \"r\"},"
                        + "{\"type\": \"works_at\", \"from\": \"r9\", \"to\": \"r2\", \"confidence\": 0.9, \"reason\": \"r\"},"
                        + "{\"type\": \"works_at\", \"from\": \"r1\", \"to\": \"r3\", \"confidence\": 0.9, \"reason\": \"r\"}");

        List<ProposalDraft> drafts = service.analyze(EMAIL, List.of(people(), organizations()));

        assertThat(relations(drafts)).isEmpty();
        assertThat(drafts).hasSize(2);
    }

    @Test
    void analyze_UndirectedRelationsMayBeStatedInEitherDirection() {
        when(relationTypeService.findAll()).thenReturn(List.of(
                new RelationTypeDto(11L, "knows", "knows", false, new LinkedHashMap<>(), Creator.USER).fromCollectionId(1L).toCollectionId(2L)));
        answerWith(PERSON + "," + ORGANIZATION,
                "{\"type\": \"knows\", \"from\": \"r2\", \"to\": \"r1\", \"confidence\": 0.9, \"reason\": \"r\"}");

        ProposalDraft relation = relations(service.analyze(EMAIL, List.of(people(), organizations()))).getFirst();

        assertThat(relation.payload()).containsEntry("from", Map.of("ref", "r1")).containsEntry("to", Map.of("ref", "r2"));
    }

    @Test
    void analyze_RepeatedRelationsAreEmittedOnce() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        answerWith(PERSON + "," + ORGANIZATION, WORKS_AT + "," + WORKS_AT);

        assertThat(relations(service.analyze(EMAIL, List.of(people(), organizations())))).hasSize(1);
    }

    @Test
    void analyze_RelationsAreAskedInASecondCallThatListsTheItemsAndTheRelationTypes() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        answerWith(PERSON + "," + ORGANIZATION, WORKS_AT);

        service.analyze(EMAIL, List.of(people(), organizations()));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<tools.jackson.databind.JsonNode> schema = ArgumentCaptor.forClass(tools.jackson.databind.JsonNode.class);
        verify(llmClient, times(2)).completeJson(eq("extractor"), system.capture(), user.capture(), schema.capture());
        // the extraction itself never sees the relation types: they make the model invent records just to relate them
        assertThat(user.getAllValues().get(0)).doesNotContain("Relation types");
        assertThat(schema.getAllValues().get(0).toString()).doesNotContain("relations");
        assertThat(user.getAllValues().get(1)).contains("Relation types").contains("works_at").contains("people → organizations")
                .contains("works at").contains("r1: people").contains("Anna Berger").contains("r2: organizations").contains("Acme")
                .contains("Interview on Friday");
        assertThat(system.getAllValues().get(1)).contains("relations");
        assertThat(schema.getAllValues().get(1).toString()).contains("relations").contains("works_at");
    }

    @Test
    void analyze_TheRelationsCallAsksForShortReasonsAndEachRelationOnce() {
        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        answerWith(PERSON + "," + ORGANIZATION, WORKS_AT);

        service.analyze(EMAIL, List.of(people(), organizations()));

        // Writing the answer is ~90 % of a call's time. Short reasons cut the relations answer by 60 %; in the extraction
        // call they saved only ~6 % and compact one-line JSON cost far more quality (see docs/ai-v2-plan.md, step 3).
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(2)).completeJson(eq("extractor"), system.capture(), anyString(), any());
        assertThat(system.getAllValues()).allSatisfy(prompt -> assertThat(prompt).doesNotContain("compact JSON"));
        assertThat(system.getAllValues().get(1)).contains("at most 12 words").contains("each relation once");
    }

    @Test
    void analyze_WithoutRelationTypesOrWithASingleItemTheModelIsNotAskedForRelations() {
        answerWith(PERSON + "," + ORGANIZATION, "");
        service.analyze(EMAIL, List.of(people(), organizations()));
        verify(llmClient, times(1)).completeJson(any(), any(), any(), any());

        when(relationTypeService.findAll()).thenReturn(List.of(worksAt()));
        answerWith(PERSON, "");
        service.analyze(EMAIL, List.of(people(), organizations()));
        verify(llmClient, times(2)).completeJson(any(), any(), any(), any());
    }
}
