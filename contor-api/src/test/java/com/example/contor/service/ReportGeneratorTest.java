package com.example.contor.service;

import com.example.contor.ai.EmailParagraphs;
import com.example.contor.ai.EvidencePack;
import com.example.contor.ai.OllamaException;
import com.example.contor.ai.ReportDraft;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.CitationType;
import com.example.contor.domain.ClaimVerification;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportBlock;
import com.example.contor.domain.ReportBlockKind;
import com.example.contor.domain.ReportCitation;
import com.example.contor.domain.ReportClaim;
import com.example.contor.domain.ReportFinding;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.ReportSection;
import com.example.contor.domain.ReportSectionKind;
import com.example.contor.domain.ReportStatus;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportGeneratorTest {

    // R1 Order 4711: F1 expected 2026-09-02, F2 total 110 EUR | R2 Delivery 88: F3 delivered 2026-09-07 | E1 (#7): P1.1, P1.2
    private static final EvidencePack PACK = EvidencePack.builder()
            .record(new EvidencePack.RecordInput(1L, "Orders", 4711L, "Order 4711", true, List.of(
                    new EvidencePack.FieldInput("expected_delivery", "Expected delivery", FieldType.DATE, "2026-09-02"),
                    new EvidencePack.FieldInput("total", "Total", FieldType.MONEY, Map.of("amount", 110, "currency", "EUR")))))
            .record(new EvidencePack.RecordInput(2L, "Deliveries", 88L, "Delivery 88", false, List.of(
                    new EvidencePack.FieldInput("delivered_at", "Delivered at", FieldType.DATE, "2026-09-07"))))
            .email(new EvidencePack.EmailInput(7L, LocalDate.of(2026, 9, 4), "Carrier", "support@carrier.example", false, "Delay notice",
                    EmailParagraphs.split("Your shipment is held at customs.\n\nWe delivered it on 2026-09-06.", null)))
            .build("", EvidencePack.DEFAULT_BUDGET);

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private EmailMessageRepository emailMessageRepository;
    @Mock
    private ReportScopeService reportScopeService;
    @Mock
    private ReportModelService reportModelService;
    @Mock
    private AiSettingsService aiSettingsService;
    @Mock
    private AuditService auditService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ReportGenerator generator;
    private Report report;

    @BeforeEach
    void setUp() {
        generator = new ReportGenerator(reportRepository, collectionRepository, emailMessageRepository, reportScopeService,
                reportModelService, aiSettingsService, auditService, transactionManager,
                Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC));
        Collection orders = Collection.builder().id(new Collection.CollectionId(1L)).name("Orders").build();
        report = Report.builder().id(new Report.ReportId(5L)).collection(orders).recordId(4711L).prompt("What went wrong?")
                .scope(new ReportScope(List.of(new RecordRef(1L, 4711L), new RecordRef(2L, 88L)), 1, List.of()))
                .createdByUser(AppUser.builder().id(new AppUser.AppUserId(1L)).build()).build();
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.of(report));
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRepository.getReferenceById(any())).thenAnswer(inv -> Collection.builder().id(inv.getArgument(0)).build());
        when(emailMessageRepository.getReferenceById(any())).thenAnswer(inv -> EmailMessage.builder().id(inv.getArgument(0)).build());
        when(aiSettingsService.settingsFor(new AppUser.AppUserId(1L))).thenReturn(AiSettings.builder().mailAccess(true).build());
        when(reportScopeService.collect(any(), eq("What went wrong?"), eq(new AppUser.AppUserId(1L)), eq(true))).thenReturn(PACK);
        when(reportModelService.model()).thenReturn("extractor");
    }

    @Test
    void generate_ReadyWithTheDocumentsSectionsBlocksTimelineAndMarkers() {
        draft(new ReportDraft("Order 4711 review", List.of(
                section("", paragraph(claim("The order was due on 2026-09-02 and arrived on 2026-09-07.", "F1", "F3"))),
                section("Reasons", list(ReportBlockKind.NUMBERED, claim("**Customs:** the shipment was held.", "P1.1"), claim("Bad luck.")))),
                true));

        generator.generate(5L);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.READY);
        assertThat(report.getTitle()).isEqualTo("Order 4711 review");
        assertThat(report.getModel()).isEqualTo("extractor");
        assertThat(report.getPromptVersion()).isEqualTo(ReportModelService.PROMPT_VERSION);
        assertThat(report.getGeneratedAt()).isEqualTo(Instant.parse("2026-10-14T10:00:00Z"));
        assertThat(report.getScope().emailIds()).containsExactly(7L);
        assertThat(report.getClaimsTotal()).isEqualTo(3);
        assertThat(report.getClaimsVerified()).as("sentences and items with a source").isEqualTo(2);
        assertThat(report.getClaimsRemoved()).isZero();
        assertThat(report.getSections()).extracting(ReportSection::getKind)
                .containsExactly(ReportSectionKind.SUMMARY, ReportSectionKind.CUSTOM, ReportSectionKind.TIMELINE);
        assertThat(report.getSections()).extracting(ReportSection::getTitle).containsExactly("Answer", "Reasons", "Timeline");

        ReportBlock lead = report.getSections().getFirst().getBlocks().getFirst();
        assertThat(lead.getKind()).isEqualTo(ReportBlockKind.PARAGRAPH);
        ReportClaim answer = lead.getClaims().getFirst();
        assertThat(answer.getBlock()).isSameAs(lead);
        assertThat(answer.getSection()).isSameAs(report.getSections().getFirst());
        assertThat(answer.getVerification()).isEqualTo(ClaimVerification.SUPPORTED);
        assertThat(answer.getCitations()).extracting(ReportCitation::getMarker).containsExactly(1, 2);
        ReportCitation due = answer.getCitations().getFirst();
        assertThat(due.getType()).isEqualTo(CitationType.FIELD);
        assertThat(due.getFieldSlug()).isEqualTo("expected_delivery");
        assertThat(due.getValueSnapshot()).containsEntry("value", "2026-09-02");

        ReportBlock reasons = report.getSections().get(1).getBlocks().getFirst();
        assertThat(reasons.getKind()).isEqualTo(ReportBlockKind.NUMBERED);
        assertThat(reasons.getClaims()).extracting(ReportClaim::getText).containsExactly("**Customs:** the shipment was held.", "Bad luck.");
        assertThat(reasons.getClaims()).extracting(ReportClaim::getPosition).containsExactly(0, 1);
        assertThat(reasons.getClaims().getFirst().getCitations()).singleElement().satisfies(citation -> {
            assertThat(citation.getMarker()).isEqualTo(3);
            assertThat(citation.getEmail().getId().id()).isEqualTo(7L);
            assertThat(citation.getSegmenterVersion()).isEqualTo(EmailParagraphs.SEGMENTER_VERSION);
        });

        ReportSection timeline = report.getSections().get(2);
        assertThat(timeline.getFindings()).extracting(ReportFinding::getTitle).containsExactly("2026-09-02", "2026-09-04", "2026-09-07");
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    void generate_SentencesAreKeptWithOrWithoutASourceAndNothingIsChecked() {
        draft(new ReportDraft("", List.of(section("", paragraph(claim("The total was 1,300 EUR.", "F2"), claim("A second carrier would have helped.")))),
                false));

        generator.generate(5L);

        List<ReportClaim> claims = report.getSections().getFirst().getBlocks().getFirst().getClaims();
        assertThat(claims).extracting(ReportClaim::getText).containsExactly("The total was 1,300 EUR.", "A second carrier would have helped.");
        assertThat(claims).extracting(ReportClaim::getVerification).containsOnly(ClaimVerification.SUPPORTED);
        assertThat(claims.get(1).getCitations()).isEmpty();
        assertThat(report.getClaimsTotal()).isEqualTo(2);
        assertThat(report.getClaimsVerified()).isEqualTo(1);
        assertThat(report.getTitle()).as("the anchor's title without a drafted one").isEqualTo("Order 4711");
        assertThat(report.getSections()).as("no timeline unless the model asks for one")
                .extracting(ReportSection::getKind).doesNotContain(ReportSectionKind.TIMELINE);
    }

    @Test
    void generate_ATableKeepsEveryCellWithItsRowColumnAndSources() {
        draft(new ReportDraft("Pros and cons", List.of(section("Carrier", table(List.of("Pro", "Con"),
                List.of(claim("Fast"), claim("Expensive", "F2")),
                List.of(claim("open"), claim("open"))))), false));

        generator.generate(5L);

        ReportSection section = report.getSections().getFirst();
        assertThat(section.getKind()).as("a document starting with a heading has no answer section").isEqualTo(ReportSectionKind.CUSTOM);
        ReportBlock table = section.getBlocks().getFirst();
        assertThat(table.getKind()).isEqualTo(ReportBlockKind.TABLE);
        assertThat(table.getColumns()).containsExactly("Pro", "Con");
        assertThat(table.getClaims()).as("repeated cells are kept").extracting(ReportClaim::getText)
                .containsExactly("Fast", "Expensive", "open", "open");
        assertThat(table.getClaims()).extracting(ReportClaim::getRowIndex).containsExactly(0, 0, 1, 1);
        assertThat(table.getClaims()).extracting(ReportClaim::getColumnIndex).containsExactly(0, 1, 0, 1);
        assertThat(table.getClaims().get(1).getCitations()).extracting(ReportCitation::getFieldSlug).containsExactly("total");
        assertThat(report.getClaimsTotal()).as("a table counts its rows").isEqualTo(2);
        assertThat(report.getClaimsVerified()).isEqualTo(1);
    }

    @Test
    void generate_AVerbatimQuoteIsHighlightedInItsSource() {
        draft(new ReportDraft("", List.of(section("", new ReportDraft.Block(ReportBlockKind.QUOTE, List.of(),
                List.of(claim("„held at customs“", "P1.1")), List.of()))), false));

        generator.generate(5L);

        ReportClaim quote = report.getSections().getFirst().getBlocks().getFirst().getClaims().getFirst();
        assertThat(quote.getCitations()).singleElement().satisfies(citation -> {
            assertThat(citation.getQuote()).isEqualTo("held at customs");
            assertThat(citation.isSupportingQuote()).isFalse();
        });
    }

    @Test
    void generate_ATimelineTheModelWroteItselfIsNotAddedTwice() {
        draft(new ReportDraft("", List.of(
                section("", paragraph(claim("It was held at customs.", "P1.1"))),
                section("Timeline of events", list(ReportBlockKind.BULLETS, claim("2026-09-04: held at customs.", "P1.1")))), true));

        generator.generate(5L);

        assertThat(report.getSections()).extracting(ReportSection::getKind).doesNotContain(ReportSectionKind.TIMELINE);
    }

    @Test
    void generate_CodesInTheTextBecomeNamesAndSources() {
        draft(new ReportDraft("", List.of(section("", paragraph(claim("Record R1 was due on 2026-09-02 (F1).")))), false));

        generator.generate(5L);

        ReportClaim claim = report.getSections().getFirst().getBlocks().getFirst().getClaims().getFirst();
        assertThat(claim.getText()).isEqualTo("Order 4711 was due on 2026-09-02.");
        assertThat(claim.getCitations()).extracting(ReportCitation::getType).containsExactly(CitationType.RECORD, CitationType.FIELD);
    }

    @Test
    void generate_ASentenceIsStoredOnceAndEmptySectionsAreLeftOut() {
        draft(new ReportDraft("", List.of(
                section("", paragraph(claim("It was held at customs.", "P1.1"))),
                section("Next steps", list(ReportBlockKind.BULLETS, claim("It was held at  customs.", "P1.1")))), false));

        generator.generate(5L);

        assertThat(report.getSections()).extracting(ReportSection::getTitle).containsExactly("Answer");
        assertThat(report.getClaimsTotal()).isEqualTo(1);
    }

    @Test
    void generate_AFollowUpLeavesOutWhatTheReportAlreadySaidAndHasNoTimeline() {
        Report parent = Report.builder().id(new Report.ReportId(4L)).build();
        ReportSection summary = ReportSection.builder().report(parent).position(0).kind(ReportSectionKind.SUMMARY).title("Answer").build();
        ReportBlock block = ReportBlock.builder().section(summary).position(0).kind(ReportBlockKind.PARAGRAPH).build();
        block.getClaims().add(ReportClaim.builder().section(summary).block(block).text("It was held at customs.")
                .verification(ClaimVerification.SUPPORTED).build());
        summary.getBlocks().add(block);
        summary.getClaims().add(ReportClaim.builder().section(summary).text("An older sentence.").verification(ClaimVerification.SUPPORTED).build());
        parent.getSections().add(summary);
        report.setParentReport(parent);
        draft(new ReportDraft("", List.of(
                section("", paragraph(claim("The carrier chose a slow route.", "P1.1"))),
                section("Next steps", list(ReportBlockKind.BULLETS, claim("It was held at customs.", "P1.1"), claim("Ask for a faster route.")))),
                true));

        generator.generate(5L);

        verify(reportModelService).draft(PACK, "What went wrong?", List.of("An older sentence.", "It was held at customs."));
        assertThat(report.getSections()).flatExtracting(ReportSection::getBlocks).flatExtracting(ReportBlock::getClaims)
                .extracting(ReportClaim::getText).containsExactly("The carrier chose a slow route.", "Ask for a faster route.");
        assertThat(report.getSections()).as("the timeline is in the report the follow-up belongs to")
                .extracting(ReportSection::getKind).doesNotContain(ReportSectionKind.TIMELINE);
    }

    @Test
    void generate_WithoutARequestTheFirstSectionIsTheSummaryWithATimeline() {
        report.setPrompt(null);
        when(reportScopeService.collect(any(), eq(""), eq(new AppUser.AppUserId(1L)), eq(true))).thenReturn(PACK);
        draft(new ReportDraft("", List.of(section("", paragraph(claim("It was held at customs.", "P1.1")))), false));

        generator.generate(5L);

        assertThat(report.getSections()).extracting(ReportSection::getTitle).containsExactly("Summary", "Timeline");
    }

    @Test
    void generate_ModelFailureMarksTheReportFailed() {
        when(reportModelService.draft(any(), anyString(), anyList())).thenThrow(new OllamaException("connection refused"));

        generator.generate(5L);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.getFailureReason()).contains("Ollama");
        assertThat(report.getSections()).isEmpty();
        verify(auditService, never()).record(any());
    }

    @Test
    void generate_ALinkageErrorAlsoMarksTheReportFailedInsteadOfLeavingItInProgress() {
        when(reportModelService.draft(any(), anyString(), anyList())).thenThrow(new NoClassDefFoundError("ReportGenerator$1"));

        generator.generate(5L);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.getFailureReason()).isEqualTo("Generating the report failed.");
    }

    private void draft(ReportDraft draft) {
        when(reportModelService.draft(any(), anyString(), anyList())).thenReturn(draft);
    }

    private static ReportDraft.Claim claim(String text, String... refs) {
        return new ReportDraft.Claim(text, List.of(refs));
    }

    private static ReportDraft.Section section(String title, ReportDraft.Block... blocks) {
        return new ReportDraft.Section(title, List.of(blocks));
    }

    private static ReportDraft.Block paragraph(ReportDraft.Claim... sentences) {
        return list(ReportBlockKind.PARAGRAPH, sentences);
    }

    private static ReportDraft.Block list(ReportBlockKind kind, ReportDraft.Claim... items) {
        return new ReportDraft.Block(kind, List.of(), List.of(items), List.of());
    }

    @SafeVarargs
    private static ReportDraft.Block table(List<String> columns, List<ReportDraft.Claim>... rows) {
        return new ReportDraft.Block(ReportBlockKind.TABLE, columns, List.of(), List.of(rows));
    }
}
