package com.example.contor.service;

import com.example.contor.ai.CollectionPlan;
import com.example.contor.ai.EmbeddingClient;
import com.example.contor.ai.EvidencePack;
import com.example.contor.ai.OllamaException;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.config.AiProperties;
import com.example.contor.config.MailContextProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import com.example.contor.domain.Relation;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.Slug;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailEmbeddingRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.NearEmail;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.persistence.RelationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportScopeServiceTest {

    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private RelationRepository relationRepository;
    @Mock
    private RecordSourceRepository recordSourceRepository;
    @Mock
    private EmailMessageRepository emailMessageRepository;
    @Mock
    private RecordService recordService;
    @Mock
    private EmailEmbeddingRepository emailEmbeddingRepository;
    @Mock
    private CollectionAnalysisService collectionAnalysisService;
    @Mock
    private EmbeddingClient embeddingClient;
    @Spy
    private AiProperties aiProperties = new AiProperties(null, new AiProperties.Models("router", "extractor", "embedder"), null,
            ZoneId.of("Europe/Berlin"));
    @Spy
    private MailContextProperties mailContextProperties = new MailContextProperties(true, 6000, 5, 5, 0.25, Duration.ofDays(180));
    @InjectMocks
    private ReportScopeService reportScopeService;

    private Collection orders;
    private Collection deliveries;
    private Collection carriers;
    private final AppUser.AppUserId owner = new AppUser.AppUserId(1L);

    @BeforeEach
    void setUp() {
        orders = collection(1L, "orders", "Orders");
        field(orders, "expected_delivery", "Expected delivery", FieldType.DATE);
        deliveries = collection(2L, "deliveries", "Deliveries");
        field(deliveries, "delivered_at", "Delivered at", FieldType.DATE);
        carriers = collection(3L, "carriers", "Carriers");
        record(orders, 4711L, "Order 4711", Map.of("expected_delivery", "2026-09-02"));
        record(deliveries, 88L, "Delivery 88", Map.of("delivered_at", "2026-09-07"));
        record(carriers, 5L, "Fast Freight", Map.of());
        // orders/4711 → deliveries/88 → carriers/5
        relations(orders, 4711L, relation(10L, orders, 4711L, "delivered by", deliveries, 88L));
        relations(deliveries, 88L, relation(10L, orders, 4711L, "delivered by", deliveries, 88L),
                relation(11L, deliveries, 88L, "carried by", carriers, 5L));
        relations(carriers, 5L, relation(11L, deliveries, 88L, "carried by", carriers, 5L));
    }

    @Test
    void scope_IncludesConnectedRecordsWithinDepth() {
        ReportScope scope = reportScopeService.scope(new RecordRef(1L, 4711L), List.of(new RecordRef(2L, 88L), new RecordRef(3L, 5L)), 2);

        assertThat(scope.records()).containsExactly(new RecordRef(1L, 4711L), new RecordRef(2L, 88L), new RecordRef(3L, 5L));
        assertThat(scope.depth()).isEqualTo(2);
    }

    @Test
    void scope_RecordBeyondDepthRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> reportScopeService.scope(new RecordRef(1L, 4711L), List.of(new RecordRef(3L, 5L)), 1));
    }

    @Test
    void scope_UnconnectedRecordRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> reportScopeService.scope(new RecordRef(1L, 4711L), List.of(new RecordRef(3L, 99L)), 2));
    }

    @Test
    void scope_AnchorAloneByDefault() {
        ReportScope scope = reportScopeService.scope(new RecordRef(1L, 4711L), null, 0);

        assertThat(scope.records()).containsExactly(new RecordRef(1L, 4711L));
        verify(relationRepository, never()).findByFromCollection_IdAndFromRecordId(any(), any());
    }

    @Test
    void collect_BuildsFactsConnectionsAndTheOwnersEmailsWithTheirThreads() {
        var account = MailAccount.builder().id(new MailAccount.MailAccountId(3L)).owner(AppUser.builder().id(owner).build()).build();
        var otherAccount = MailAccount.builder().id(new MailAccount.MailAccountId(4L)).owner(AppUser.builder().id(new AppUser.AppUserId(2L)).build()).build();
        var delay = email(20L, account, "conv-1", "inbox", "Delay notice", "Your shipment is held at customs.");
        var reply = email(21L, account, "conv-1", "sent", "Re: Delay notice", "Please hurry.");
        var foreign = email(22L, otherAccount, null, "inbox", "Someone else's mail", "Private.");
        when(recordSourceRepository.findByCollection_IdAndRecordId(orders.getId(), 4711L)).thenReturn(List.of(
                RecordSource.builder().collection(orders).recordId(4711L).email(delay).role(RecordSourceRole.MENTIONED_IN).build(),
                RecordSource.builder().collection(orders).recordId(4711L).email(foreign).role(RecordSourceRole.MENTIONED_IN).build()));
        when(emailMessageRepository.findThread(account.getId(), "conv-1")).thenReturn(List.of(delay, reply));
        var scope = new ReportScope(List.of(new RecordRef(1L, 4711L), new RecordRef(2L, 88L)), 1, null);

        var pack = reportScopeService.collect(scope, "Why was it late?", owner, true);

        assertThat(pack.render()).contains("[R1] Orders: Order 4711 (the record the report is about)",
                "[F1] Order 4711 · Expected delivery = 2026-09-02", "[R2] Deliveries: Delivery 88",
                "[L1] Order 4711 — delivered by → Delivery 88", "sent by you");
        assertThat(pack.render()).as("the relation to a record outside the scope").doesNotContain("carried by");
        assertThat(pack.emailIds()).containsExactlyInAnyOrder(20L, 21L);
    }

    @Test
    void collect_AddsRelatedEmailsFromTheMailboxAndTheRecordsLinkedToThem() {
        var account = MailAccount.builder().id(new MailAccount.MailAccountId(3L)).owner(AppUser.builder().id(owner).build()).build();
        var delay = email(20L, account, null, "inbox", "Delay notice", "Your shipment is held at customs.");
        var similar = email(30L, account, null, "inbox", "Another delay", "Order 4800 is stuck at customs too.");
        when(recordSourceRepository.findByCollection_IdAndRecordId(orders.getId(), 4711L)).thenReturn(List.of(
                RecordSource.builder().collection(orders).recordId(4711L).email(delay).role(RecordSourceRole.MENTIONED_IN).build()));
        when(emailEmbeddingRepository.findNearest(eq(owner), eq(20L), eq("embedder"), any(), any(), eq(0.25), anyInt()))
                .thenReturn(List.of(new NearEmail(20L, 0.0), new NearEmail(30L, 0.1)));
        when(emailMessageRepository.findAllById(List.of(new EmailMessage.EmailMessageId(30L)))).thenReturn(List.of(similar));
        when(recordSourceRepository.findByEmail_IdIn(List.of(new EmailMessage.EmailMessageId(30L)))).thenReturn(List.of(
                RecordSource.builder().collection(orders).recordId(4800L).email(similar).role(RecordSourceRole.CREATED_FROM).build()));
        record(orders, 4800L, "Order 4800", Map.of());
        var scope = new ReportScope(List.of(new RecordRef(1L, 4711L)), 0, null);

        var pack = reportScopeService.collect(scope, "What else went wrong?", owner, true);

        assertThat(pack.emailIds()).containsExactly(20L, 30L);
        assertThat(pack.render()).contains("Other records (found through related emails):", "Orders: Order 4800",
                "Related emails from the rest of the mailbox", "Order 4800 is stuck at customs too.");
    }

    @Test
    void collect_WithoutMailAccessUsesNoEmails() {
        var scope = new ReportScope(List.of(new RecordRef(1L, 4711L)), 0, null);

        var pack = reportScopeService.collect(scope, "", owner, false);

        assertThat(pack.emailIds()).isEmpty();
        verify(recordSourceRepository, never()).findByCollection_IdAndRecordId(any(), eq(4711L));
        verify(emailEmbeddingRepository, never()).findNearest(any(), any(), any(), any(), any(), anyDouble(), anyInt());
    }

    @Test
    void collectCollection_PacksTheFiguresTheOverviewAndTheRecordsThatFit() {
        var scope = new ReportScope.CollectionScope(List.of("expected_delivery:notempty"), null);
        var plan = new CollectionPlan(List.of(new CollectionPlan.Analysis("Late", "orders", "", "count", false, 5, List.of())));
        var analysis = new EvidencePack.AnalysisInput("Late orders", "3 Orders, all together, counted", List.of("#", "All", "Orders"), 2,
                List.of(new EvidencePack.AnalysisRow(List.of("1", "All matching records", "3"), List.of())), null);
        when(collectionAnalysisService.run(plan, 1L, scope)).thenReturn(List.of(analysis));
        when(collectionAnalysisService.overview(1L, scope)).thenReturn("Overview of Orders:\nOrders: 3 records");
        when(recordService.listRecords(1L, scope.filters(), null, null, 0, ReportScopeService.COLLECTION_RECORDS)).thenReturn(new RecordPageDto(
                new ArrayList<>(List.of(recordDto(orders, 4712L, "Order 4712", Map.of("expected_delivery", "2026-09-05")),
                        recordDto(orders, 4711L, "Order 4711", Map.of()))), 0, ReportScopeService.COLLECTION_RECORDS, 3L, 1));

        EvidencePack pack = reportScopeService.collectCollection(1L, scope, plan, "How many are late?");

        assertThat(pack.analyses()).containsExactly(analysis);
        assertThat(pack.render()).startsWith("Figures computed by the app").contains("[A1] Late orders", "Overview of Orders:",
                "Records of Orders (newest first, 2 of 3 matching records):", "[R1] Orders: Order 4712",
                "[F1] Order 4712 · Expected delivery = 2026-09-05", "[R2] Orders: Order 4711");
        assertThat(pack.render()).doesNotContain("the record the report is about");
        assertThat(pack.emailIds()).isEmpty();
    }

    private static RecordDto recordDto(Collection collection, long recordId, String title, Map<String, Object> values) {
        return new RecordDto(recordId, collection.getId().id(), new HashMap<>(values), new HashMap<>(),
                OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC), "USER").title(title);
    }

    @Test
    void collectQuestion_FoundRecordsWithTheirConnectionsAndNoMailWithoutAccess() {
        var pack = reportScopeService.collectQuestion(List.of(new RecordRef(1L, 4711L)), "Why was order 4711 late?", List.of(), owner, false, true);

        assertThat(pack.render()).contains("Orders: Order 4711", "Deliveries: Delivery 88", "delivered by")
                .doesNotContain("the record the report is about");
        assertThat(pack.render()).as("connections of connections are not followed").doesNotContain("Fast Freight");
        assertThat(pack.emailIds()).isEmpty();
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void collectQuestion_NothingFoundGivesAnEmptyPackWithoutMail() {
        var pack = reportScopeService.collectQuestion(List.of(), "Hello", List.of(), owner, false, true);

        assertThat(pack.sources()).isEmpty();
    }

    @Test
    void collectQuestion_AddsMailFromThePeopleFoundAndMailCloseToTheQuestion() {
        var people = collection(6L, "people", "People");
        field(people, "email", "Email", FieldType.EMAIL);
        record(people, 9L, "Anna Kessler", Map.of("email", "anna@kessler.example"));
        var account = MailAccount.builder().id(new MailAccount.MailAccountId(3L)).owner(AppUser.builder().id(owner).build()).build();
        var fromAnna = email(40L, account, null, "inbox", "Our order", "Where is our order?");
        var close = email(41L, account, null, "inbox", "Late again", "The parcel is late again.");
        when(emailMessageRepository.findFromSenderBefore(eq(owner), eq("anna@kessler.example"), any(), any())).thenReturn(List.of(fromAnna));
        float[] vector = {0.1f, 0.2f};
        when(embeddingClient.embed("What did Anna write?")).thenReturn(vector);
        when(emailEmbeddingRepository.findNearestTo(eq(owner), eq(vector), eq("embedder"), anyDouble(), anyInt()))
                .thenReturn(List.of(new NearEmail(40L, 0.2), new NearEmail(41L, 0.3)));
        when(emailMessageRepository.findAllById(List.of(new EmailMessage.EmailMessageId(41L)))).thenReturn(List.of(close));

        var pack = reportScopeService.collectQuestion(List.of(new RecordRef(6L, 9L)), "What did Anna write?", List.of(), owner, true, true);

        assertThat(pack.emailIds()).containsExactly(40L, 41L);
        assertThat(pack.render()).contains("Where is our order?", "The parcel is late again.");
    }

    @Test
    void collectQuestion_SkipsMailboxesTheAiMayNotRead() {
        var people = collection(6L, "people", "People");
        field(people, "email", "Email", FieldType.EMAIL);
        record(people, 9L, "Anna Kessler", Map.of("email", "anna@kessler.example"));
        var off = MailAccount.builder().id(new MailAccount.MailAccountId(3L)).owner(AppUser.builder().id(owner).build()).aiEnabled(false).build();
        var hidden = MailAccount.builder().id(new MailAccount.MailAccountId(4L)).owner(AppUser.builder().id(owner).build()).hidden(true).build();
        when(emailMessageRepository.findFromSenderBefore(eq(owner), eq("anna@kessler.example"), any(), any())).thenReturn(List.of(
                email(40L, off, null, "inbox", "Off", "Not for the AI."), email(42L, hidden, null, "inbox", "Hidden", "In a hidden mailbox.")));
        when(embeddingClient.embed(any())).thenReturn(new float[]{0.1f});

        assertThat(reportScopeService.collectQuestion(List.of(new RecordRef(6L, 9L)), "What did Anna write?", List.of(), owner, true, false).emailIds())
                .isEmpty();
        assertThat(reportScopeService.collectQuestion(List.of(new RecordRef(6L, 9L)), "What did Anna write?", List.of(), owner, true, true).emailIds())
                .containsExactly(42L);
    }

    @Test
    void collectQuestion_WithoutTheEmbeddingModelStillUsesTheRecordsMail() {
        var people = collection(6L, "people", "People");
        field(people, "email", "Email", FieldType.EMAIL);
        record(people, 9L, "Anna Kessler", Map.of("email", "anna@kessler.example"));
        var account = MailAccount.builder().id(new MailAccount.MailAccountId(3L)).owner(AppUser.builder().id(owner).build()).build();
        when(emailMessageRepository.findFromSenderBefore(eq(owner), eq("anna@kessler.example"), any(), any()))
                .thenReturn(List.of(email(40L, account, null, "inbox", "Our order", "Where is our order?")));
        when(embeddingClient.embed(any())).thenThrow(new OllamaException("model missing"));

        assertThat(reportScopeService.collectQuestion(List.of(new RecordRef(6L, 9L)), "What did Anna write?", List.of(), owner, true, true).emailIds())
                .containsExactly(40L);
    }

    @Test
    void collectQuestion_FindsMailByThePlansSearchesWithoutRecordsOrEmbeddings() {
        var account = MailAccount.builder().id(new MailAccount.MailAccountId(3L)).owner(AppUser.builder().id(owner).build()).build();
        var fromHelga = email(50L, account, null, "inbox", "Budget 2027", "Please send the budget by Friday.");
        when(emailMessageRepository.findAll(ArgumentMatchers.<Specification<EmailMessage>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fromHelga)));
        when(embeddingClient.embed(any())).thenThrow(new OllamaException("model missing"));

        var pack = reportScopeService.collectQuestion(List.of(), "What did Helga write?", List.of("Helga Sommerfeld"), owner, true, true);

        assertThat(pack.emailIds()).containsExactly(50L);
        assertThat(pack.render()).contains("Please send the budget by Friday.");
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(emailMessageRepository).findAll(ArgumentMatchers.<Specification<EmailMessage>>any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(ReportScopeService.SEARCH_EMAILS);
        assertThat(page.getValue().getSort().getOrderFor("receivedAt").isDescending()).isTrue();
    }

    @Test
    void collectQuestion_DoesNotSearchMailWithoutAccess() {
        reportScopeService.collectQuestion(List.of(), "What did Helga write?", List.of("Helga Sommerfeld"), owner, false, true);

        verify(emailMessageRepository, never()).findAll(ArgumentMatchers.<Specification<EmailMessage>>any(), any(Pageable.class));
    }

    private static Collection collection(long id, String slug, String name) {
        return Collection.builder().id(new Collection.CollectionId(id)).slug(new Slug(slug)).name(name).tableName("c_" + slug)
                .createdBy(CreatedBy.USER).fields(new ArrayList<>()).build();
    }

    private static void field(Collection collection, String slug, String name, FieldType type) {
        collection.getFields().add(CollectionField.builder().id(new CollectionField.CollectionFieldId((long) collection.getFields().size() + 1))
                .collection(collection).slug(new Slug(slug)).name(name).type(type).options(new HashMap<>())
                .position(collection.getFields().size() + 1).columnName(slug).build());
    }

    private void record(Collection collection, long recordId, String title, Map<String, Object> values) {
        when(collectionRepository.findById(collection.getId())).thenReturn(Optional.of(collection));
        when(recordService.getRecord(collection.getId().id(), recordId)).thenReturn(new RecordDto(recordId, collection.getId().id(),
                new HashMap<>(values), new HashMap<>(), OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC), "USER").title(title));
    }

    private void relations(Collection collection, long recordId, Relation... relations) {
        List<Relation> from = new ArrayList<>();
        List<Relation> to = new ArrayList<>();
        for (Relation relation : relations) {
            if (relation.getFromCollection() == collection && relation.getFromRecordId() == recordId) {
                from.add(relation);
            } else {
                to.add(relation);
            }
        }
        when(relationRepository.findByFromCollection_IdAndFromRecordId(collection.getId(), recordId)).thenReturn(from);
        when(relationRepository.findByToCollection_IdAndToRecordId(collection.getId(), recordId)).thenReturn(to);
    }

    private static Relation relation(long id, Collection from, long fromId, String name, Collection to, long toId) {
        return Relation.builder().id(new Relation.RelationId(id))
                .relationType(RelationType.builder().id(new RelationType.RelationTypeId(id)).name(name).build())
                .fromCollection(from).fromRecordId(fromId).toCollection(to).toRecordId(toId).build();
    }

    private static EmailMessage email(long id, MailAccount account, String conversationId, String folder, String subject, String body) {
        return EmailMessage.builder().id(new EmailMessage.EmailMessageId(id)).account(account).conversationId(conversationId).folder(folder)
                .fromName("Carrier Support").fromAddress("support@carrier.example").subject(subject).bodyText(body)
                .receivedAt(Instant.parse("2026-09-04T08:00:00Z")).build();
    }
}
