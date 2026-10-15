package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.api.model.NewReportCommand;
import com.example.contor.api.model.NewReportQuestionCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordRefDto;
import com.example.contor.api.model.ReportDto;
import com.example.contor.api.model.SourceState;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.CitationType;
import com.example.contor.domain.ClaimVerification;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportCitation;
import com.example.contor.domain.ReportClaim;
import com.example.contor.domain.ReportScope;
import com.example.contor.domain.ReportScope.RecordRef;
import com.example.contor.domain.ReportSection;
import com.example.contor.domain.ReportSectionKind;
import com.example.contor.domain.ReportStatus;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.exception.ReportBusyException;
import com.example.contor.exception.ReportNotFoundException;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationRepository;
import com.example.contor.persistence.ReportRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RelationRepository relationRepository;
    @Mock
    private RecordService recordService;
    @Mock
    private ReportScopeService reportScopeService;
    @Mock
    private ReportGenerator reportGenerator;
    @Mock
    private AiModelService aiModelService;
    @Mock
    private AuditService auditService;

    private final List<Runnable> queued = new ArrayList<>();
    private ReportService reportService;
    private Collection orders;

    @BeforeEach
    void setUp() {
        Executor executor = queued::add;
        reportService = new ReportService(reportRepository, collectionRepository, appUserRepository, relationRepository, recordService,
                reportScopeService, reportGenerator, aiModelService, auditService, new AiExecutors(executor, mock(Executor.class)));
        orders = Collection.builder().id(new Collection.CollectionId(1L)).name("Orders").fields(new ArrayList<>()).build();
        when(collectionRepository.getReferenceById(new Collection.CollectionId(1L))).thenReturn(orders);
        when(appUserRepository.getReferenceById(new AppUser.AppUserId(1L))).thenReturn(AppUser.builder().id(new AppUser.AppUserId(1L)).build());
        when(aiModelService.isReady()).thenReturn(true);
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(inv -> {
            Report report = inv.getArgument(0);
            if (report.getId() == null) {
                report.setId(new Report.ReportId(50L));
            }
            report.setCreatedAt(Instant.parse("2026-10-14T10:00:00Z"));
            return report;
        });
        when(recordService.getRecord(1L, 4711L)).thenReturn(record(4711L, "Order 4711", Map.of("total", Map.of("amount", 110, "currency", "EUR"))));
        var user = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReport_QueuesAReportOverTheCheckedScope() {
        var scope = new ReportScope(List.of(new RecordRef(1L, 4711L), new RecordRef(2L, 88L)), 1, List.of());
        when(reportScopeService.scope(new RecordRef(1L, 4711L), List.of(new RecordRef(2L, 88L)), 1)).thenReturn(scope);

        ReportDto dto = reportService.createReport(1L, 4711L, new NewReportCommand().prompt(" What went wrong? ")
                .connectedRecords(List.of(new RecordRefDto(2L, 88L))));

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.QUEUED);
        assertThat(saved.getValue().getPrompt()).isEqualTo("What went wrong?");
        assertThat(saved.getValue().getScope()).isEqualTo(scope);
        assertThat(saved.getValue().getCreatedByUser().getId().id()).isEqualTo(1L);
        assertThat(dto.getId()).isEqualTo(50L);
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        verify(reportGenerator).generate(50L);
    }

    @Test
    void createReport_WithoutConnectionsCoversTheRecordAlone() {
        when(reportScopeService.scope(new RecordRef(1L, 4711L), List.of(), 0))
                .thenReturn(new ReportScope(List.of(new RecordRef(1L, 4711L)), 0, List.of()));

        reportService.createReport(1L, 4711L, new NewReportCommand());

        verify(reportScopeService).scope(new RecordRef(1L, 4711L), List.of(), 0);
    }

    @Test
    void createReport_PromptTooLongRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> reportService.createReport(1L, 4711L, new NewReportCommand().prompt("x".repeat(ReportService.MAX_PROMPT + 1))));
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReport_AiNotReadyRejected() {
        when(aiModelService.isReady()).thenReturn(false);

        assertThrows(AiUnavailableException.class, () -> reportService.createReport(1L, 4711L, new NewReportCommand()));
        verify(reportRepository, never()).saveAndFlush(any());
        assertThat(queued).isEmpty();
    }

    @Test
    void askReportQuestion_QueuesAFollowUpWithTheSameScope() {
        Report parent = report(ReportStatus.READY);
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.of(parent));

        ReportDto dto = reportService.askReportQuestion(5L, new NewReportQuestionCommand("Who caused the delay?"));

        assertThat(dto.getParentReportId()).isEqualTo(5L);
        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getParentReport()).isSameAs(parent);
        assertThat(saved.getValue().getPrompt()).isEqualTo("Who caused the delay?");
        assertThat(saved.getValue().getScope().records()).isEqualTo(parent.getScope().records());
        assertThat(queued).hasSize(1);
    }

    @Test
    void askReportQuestion_WhileGeneratingConflict() {
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.of(report(ReportStatus.DRAFTING)));

        assertThrows(ReportBusyException.class, () -> reportService.askReportQuestion(5L, new NewReportQuestionCommand("Why?")));
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void askReportQuestion_BlankQuestionRejected() {
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.of(report(ReportStatus.READY)));

        assertThrows(IllegalArgumentException.class, () -> reportService.askReportQuestion(5L, new NewReportQuestionCommand("  ")));
    }

    @Test
    void regenerateReport_QueuesANewReportWithTheSamePromptAndScope() {
        Report old = report(ReportStatus.READY);
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.of(old));

        reportService.regenerateReport(5L);

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(old);
        assertThat(saved.getValue().getPrompt()).isEqualTo(old.getPrompt());
        assertThat(saved.getValue().getScope().records()).isEqualTo(old.getScope().records());
        assertThat(queued).hasSize(1);
    }

    @Test
    void getReport_MarksChangedFieldsAndForeignMailboxes() {
        Report report = report(ReportStatus.READY);
        ReportSection summary = ReportSection.builder().report(report).position(0).kind(ReportSectionKind.SUMMARY).title("Executive summary").build();
        ReportClaim claim = ReportClaim.builder().section(summary).position(0).text("The total was 100 EUR.").verification(ClaimVerification.SUPPORTED).build();
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(1).type(CitationType.FIELD).collection(orders).recordId(4711L)
                .fieldSlug("total").valueSnapshot(Map.of("value", Map.of("amount", 100, "currency", "EUR"))).build());
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(2).type(CitationType.EMAIL_PARAGRAPH).email(email(2L))
                .paragraphIndex(0).paragraphHash("h").segmenterVersion(1).quote("secret").build());
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(3).type(CitationType.EMAIL_PARAGRAPH).email(email(1L))
                .paragraphIndex(0).paragraphHash("h").segmenterVersion(1).quote("mine").build());
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(4).type(CitationType.RECORD).collection(orders).recordId(99L).build());
        summary.getClaims().add(claim);
        report.getSections().add(summary);
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.of(report));
        when(reportRepository.findByParentReport_IdOrderByCreatedAtAsc(new Report.ReportId(5L))).thenReturn(List.of());
        when(recordService.getRecord(1L, 99L)).thenThrow(new RecordNotFoundException(1L, 99L));

        ReportDto dto = reportService.getReport(5L);

        assertThat(dto.getSources()).extracting(source -> source.getSourceState())
                .containsExactly(SourceState.CHANGED, SourceState.UNAVAILABLE, SourceState.CURRENT, SourceState.DELETED);
        assertThat(dto.getSources().getFirst().getRecordTitle()).isEqualTo("Order 4711");
        assertThat(dto.getSources().get(1).getQuote()).isNull();
    }

    @Test
    void getReport_SameValueInAnotherNumberTypeIsCurrent() {
        Report report = report(ReportStatus.READY);
        ReportSection summary = ReportSection.builder().report(report).position(0).kind(ReportSectionKind.SUMMARY).title("Executive summary").build();
        ReportClaim claim = ReportClaim.builder().section(summary).position(0).text("110 EUR.").verification(ClaimVerification.SUPPORTED).build();
        claim.getCitations().add(ReportCitation.builder().claim(claim).marker(1).type(CitationType.FIELD).collection(orders).recordId(4711L)
                .fieldSlug("total").valueSnapshot(Map.of("value", Map.of("amount", 110.0, "currency", "EUR"))).build());
        summary.getClaims().add(claim);
        report.getSections().add(summary);
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.of(report));

        assertThat(reportService.getReport(5L).getSources().getFirst().getSourceState()).isEqualTo(SourceState.CURRENT);
    }

    @Test
    void getReport_NotFound() {
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.empty());

        assertThrows(ReportNotFoundException.class, () -> reportService.getReport(5L));
    }

    @Test
    void listRecordReports_SummariesWithFollowUpCounts() {
        Report report = report(ReportStatus.READY);
        when(reportRepository.findByCollection_IdAndRecordIdAndParentReportIsNullOrderByCreatedAtDesc(new Collection.CollectionId(1L), 4711L))
                .thenReturn(List.of(report));
        when(reportRepository.findByParentReport_IdOrderByCreatedAtAsc(new Report.ReportId(5L))).thenReturn(List.of(report(ReportStatus.READY)));

        var summaries = reportService.listRecordReports(1L, 4711L);

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.getId()).isEqualTo(5L);
            assertThat(summary.getFollowUpCount()).isEqualTo(1);
        });
    }

    @Test
    void listRecordReports_RecordNotFound() {
        when(recordService.getRecord(1L, 9L)).thenThrow(new RecordNotFoundException(1L, 9L));

        assertThrows(RecordNotFoundException.class, () -> reportService.listRecordReports(1L, 9L));
    }

    @Test
    void deleteReport_DeletesAndAudits() {
        Report report = report(ReportStatus.READY);
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.of(report));

        reportService.deleteReport(5L);

        verify(reportRepository).delete(report);
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    void resumeInterruptedReports_QueuesReportsARestartLeftInProgress() {
        Report drafting = report(ReportStatus.DRAFTING);
        when(reportRepository.findByStatusIn(ReportStatus.ACTIVE)).thenReturn(List.of(drafting));

        reportService.resumeInterruptedReports();

        assertThat(drafting.getStatus()).isEqualTo(ReportStatus.QUEUED);
        verify(reportRepository).saveAndFlush(drafting);
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        verify(reportGenerator).generate(5L);
    }

    @Test
    void resumeInterruptedReports_NothingLeftNothingQueued() {
        when(reportRepository.findByStatusIn(ReportStatus.ACTIVE)).thenReturn(List.of());

        reportService.resumeInterruptedReports();

        assertThat(queued).isEmpty();
    }

    @Test
    void deleteReport_NotFound() {
        when(reportRepository.findById(new Report.ReportId(5L))).thenReturn(Optional.empty());

        assertThrows(ReportNotFoundException.class, () -> reportService.deleteReport(5L));
        verify(reportRepository, never()).delete(any());
    }

    private Report report(ReportStatus status) {
        Report report = Report.builder().id(new Report.ReportId(5L)).collection(orders).recordId(4711L).prompt("What went wrong?")
                .status(status).scope(new ReportScope(List.of(new RecordRef(1L, 4711L)), 0, List.of(7L))).build();
        report.setCreatedAt(Instant.parse("2026-10-14T09:00:00Z"));
        return report;
    }

    private static RecordDto record(long id, String title, Map<String, Object> values) {
        return new RecordDto(id, 1L, new HashMap<>(values), new HashMap<>(), OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC), "USER")
                .title(title);
    }

    private static EmailMessage email(long ownerId) {
        return EmailMessage.builder().id(new EmailMessage.EmailMessageId(ownerId == 1L ? 7L : 8L)).subject("Delay")
                .account(MailAccount.builder().id(new MailAccount.MailAccountId(ownerId)).owner(AppUser.builder().id(new AppUser.AppUserId(ownerId)).build()).build())
                .build();
    }
}
