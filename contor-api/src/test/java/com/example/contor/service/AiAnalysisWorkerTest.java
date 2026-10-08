package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.EmailContent;
import com.example.contor.ai.ExtractionService;
import com.example.contor.ai.OllamaException;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AiAnalysisRun;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AnalysisRunStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.ProposalKind;
import com.example.contor.exception.RecordValidationException;
import com.example.contor.persistence.AiAnalysisRunRepository;
import com.example.contor.persistence.EmailMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisWorkerTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final Instant SINCE = Instant.parse("2026-07-10T12:00:00Z");

    @Mock
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private AiSettingsService aiSettingsService;

    @Mock
    private CollectionSchemaService collectionSchemaService;

    @Mock
    private InboxDiscoveryService inboxDiscoveryService;

    @Mock
    private ExtractionService extractionService;

    @Mock
    private ProposalService proposalService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private AiAnalysisWorker worker;
    private AiAnalysisRun run;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        worker = new AiAnalysisWorker(aiAnalysisRunRepository, emailMessageRepository, aiSettingsService, collectionSchemaService,
                inboxDiscoveryService, extractionService, proposalService, properties, new AiExecutors(Runnable::run, Runnable::run),
                transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));

        run = AiAnalysisRun.builder().id(new AiAnalysisRun.AiAnalysisRunId(10L)).user(AppUser.builder().id(OWNER).build())
                .status(AnalysisRunStatus.QUEUED).since(SINCE).build();
        lenient().when(aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(10L))).thenAnswer(inv -> Optional.of(run));
        lenient().when(aiAnalysisRunRepository.saveAndFlush(any(AiAnalysisRun.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(collectionSchemaService.findAll()).thenReturn(List.of(collection(1L, "people"), collection(3L, "events"),
                collection(4L, "tasks"), collection(5L, "job_applications")));
        lenient().when(emailMessageRepository.saveAndFlush(any(EmailMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CollectionDto collection(long id, String slug) {
        return new CollectionDto().id(id).slug(slug).name(slug);
    }

    private void settings(AiPermissionLevel categories, AiPermissionLevel records, AiPermissionLevel calendar, AiPermissionLevel tasks) {
        when(aiSettingsService.settingsFor(OWNER)).thenReturn(AiSettings.builder().mailAccess(true).historyDays(90)
                .categories(categories).records(records).calendar(calendar).tasks(tasks).build());
    }

    private EmailMessage email(long id) {
        EmailMessage email = EmailMessage.builder().id(new EmailMessage.EmailMessageId(id))
                .account(MailAccount.builder().emailAddress(new Email("me@example.com")).build())
                .fromName("Anna").fromAddress("anna@acme.com").subject("Subject " + id).bodyText("Body " + id)
                .receivedAt(NOW.minusSeconds(3600)).build();
        lenient().when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(id))).thenReturn(Optional.of(email));
        return email;
    }

    private void emailsToProcess(Long... ids) {
        when(emailMessageRepository.findIdsForProcessing(eq(OWNER), eq(ProcessingState.NEW), eq(SINCE), any())).thenReturn(List.of(ids));
    }

    private static ProposalDraft draft(long collectionId) {
        return new ProposalDraft(ProposalKind.CREATE_RECORD, Map.of("collectionId", collectionId, "title", "T"), BigDecimal.ONE, "r");
    }

    private static InboxDiscoveryService.Suggestion suggestion() {
        return new InboxDiscoveryService.Suggestion(new NewCollectionCommand("job_applications", "Job applications"), 6, "Applications");
    }

    @Test
    void run_AutoCategoriesAreCreatedThenEmailsExtracted() {
        settings(AiPermissionLevel.AUTO, AiPermissionLevel.AUTO, AiPermissionLevel.SUGGEST, AiPermissionLevel.OFF);
        when(inboxDiscoveryService.discover(any(), anyList(), any())).thenReturn(List.of(suggestion()));
        EmailMessage email = email(100L);
        emailsToProcess(100L);
        when(extractionService.analyze(any(EmailContent.class), anyList())).thenReturn(List.of(draft(1L), draft(3L)));
        when(proposalService.submitDraft(any(), eq(100L), eq(AiPermissionLevel.AUTO), eq("extractor"), any()))
                .thenReturn(ProposalService.Outcome.AUTO_APPLIED);
        when(proposalService.submitDraft(any(), eq(100L), eq(AiPermissionLevel.SUGGEST), eq("extractor"), any()))
                .thenReturn(ProposalService.Outcome.SUGGESTED);

        worker.run(10L);

        verify(collectionSchemaService).createAiCollection(argThat(c -> c.getSlug().equals("job_applications")));
        ArgumentCaptor<List<CollectionDto>> targets = ArgumentCaptor.captor();
        ArgumentCaptor<EmailContent> content = ArgumentCaptor.forClass(EmailContent.class);
        verify(extractionService).analyze(content.capture(), targets.capture());
        assertThat(targets.getValue()).extracting(CollectionDto::getSlug).containsExactly("people", "events", "job_applications");
        assertThat(content.getValue().mailbox()).isEqualTo("me@example.com");
        assertThat(content.getValue().body()).isEqualTo("Body 100");
        verify(proposalService).submitDraft(argThat(d -> d.payload().get("collectionId").equals(1L)), eq(100L), eq(AiPermissionLevel.AUTO), any(), any());
        verify(proposalService).submitDraft(argThat(d -> d.payload().get("collectionId").equals(3L)), eq(100L), eq(AiPermissionLevel.SUGGEST), any(), any());

        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
        assertThat(run.getCategoriesDiscovered()).isEqualTo(1);
        assertThat(run.getTotalEmails()).isEqualTo(1);
        assertThat(run.getProcessedEmails()).isEqualTo(1);
        assertThat(run.getAutoApplied()).isEqualTo(1);
        assertThat(run.getProposalsCreated()).isEqualTo(1);
        assertThat(run.getStartedAt()).isEqualTo(NOW);
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
        assertThat(email.getProcessingState()).isEqualTo(ProcessingState.DONE);
        assertThat(email.getProcessingVersion()).isEqualTo(AiAnalysisWorker.PROCESSING_VERSION);
        verify(emailMessageRepository).updateProcessingStateBefore(OWNER, ProcessingState.NEW, ProcessingState.SKIPPED, SINCE);
    }

    @Test
    void run_DiscoveryProgressIsStoredOnTheRun() {
        settings(AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        List<String> seen = new ArrayList<>();
        when(inboxDiscoveryService.discover(any(), anyList(), any())).thenAnswer(inv -> {
            InboxDiscoveryService.ProgressListener listener = inv.getArgument(2);
            listener.progress(0, 11);
            seen.add(run.getDiscoveryStepsDone() + "/" + run.getDiscoverySteps());
            listener.progress(4, 11);
            seen.add(run.getDiscoveryStepsDone() + "/" + run.getDiscoverySteps());
            return List.of();
        });
        emailsToProcess();

        worker.run(10L);

        assertThat(seen).containsExactly("0/11", "4/11");
    }

    @Test
    void run_ShowsTheEmailBeingReadAndClearsItAtTheEnd() {
        settings(AiPermissionLevel.OFF, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        EmailMessage email = email(100L);
        when(emailMessageRepository.getReferenceById(new EmailMessage.EmailMessageId(100L))).thenReturn(email);
        emailsToProcess(100L);
        List<String> seen = new ArrayList<>();
        when(extractionService.analyze(any(EmailContent.class), anyList())).thenAnswer(inv -> {
            seen.add(run.getCurrentEmail() != null ? run.getCurrentEmail().getSubject() : "none");
            return List.of();
        });

        worker.run(10L);

        assertThat(seen).containsExactly("Subject 100");
        assertThat(run.getCurrentEmail()).isNull();
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
    }

    @Test
    void run_SuggestedCategoriesWaitForReview() {
        settings(AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        when(inboxDiscoveryService.discover(any(), anyList(), any())).thenReturn(List.of(suggestion()));
        when(proposalService.submitDraft(any(), isNull(), eq(AiPermissionLevel.SUGGEST), eq("router"), eq(InboxDiscoveryService.PROMPT_VERSION)))
                .thenReturn(ProposalService.Outcome.SUGGESTED);

        worker.run(10L);

        verify(proposalService).submitDraft(argThat(d -> d.kind() == ProposalKind.CREATE_COLLECTION
                        && d.payload().get("slug").equals("job_applications") && d.reasoning().equals("Applications")),
                isNull(), eq(AiPermissionLevel.SUGGEST), eq("router"), eq(InboxDiscoveryService.PROMPT_VERSION));
        verify(collectionSchemaService, never()).createAiCollection(any());
        verify(extractionService, never()).analyze(any(), anyList());
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.AWAITING_REVIEW);
        assertThat(run.getCategoriesDiscovered()).isEqualTo(1);
        assertThat(run.getProposalsCreated()).isEqualTo(1);
    }

    @Test
    void run_CategoriesOffSkipsDiscovery() {
        settings(AiPermissionLevel.OFF, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        emailsToProcess();

        worker.run(10L);

        verify(inboxDiscoveryService, never()).discover(any(), anyList(), any());
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
    }

    @Test
    void run_AfterReviewContinuesWithExtraction() {
        run.setStatus(AnalysisRunStatus.EXTRACTING);
        settings(AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        emailsToProcess();

        worker.run(10L);

        verify(inboxDiscoveryService, never()).discover(any(), anyList(), any());
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
    }

    @Test
    void run_AutoApplyFailureFallsBackToASuggestion() {
        settings(AiPermissionLevel.OFF, AiPermissionLevel.AUTO, AiPermissionLevel.AUTO, AiPermissionLevel.AUTO);
        email(100L);
        emailsToProcess(100L);
        when(extractionService.analyze(any(EmailContent.class), anyList())).thenReturn(List.of(draft(1L)));
        when(proposalService.submitDraft(any(), eq(100L), eq(AiPermissionLevel.AUTO), any(), any()))
                .thenThrow(new RecordValidationException(List.of("full_name: is required")));
        when(proposalService.submitDraft(any(), eq(100L), eq(AiPermissionLevel.SUGGEST), any(), any()))
                .thenReturn(ProposalService.Outcome.SUGGESTED);

        worker.run(10L);

        assertThat(run.getProposalsCreated()).isEqualTo(1);
        assertThat(run.getAutoApplied()).isZero();
    }

    @Test
    void run_FailingEmailIsMarkedAndTheRunContinues() {
        settings(AiPermissionLevel.OFF, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        EmailMessage broken = email(100L);
        EmailMessage fine = email(101L);
        emailsToProcess(100L, 101L);
        when(extractionService.analyze(argThat(c -> c != null && c.id() == 100L), anyList())).thenThrow(new OllamaException("model crashed"));
        when(extractionService.analyze(argThat(c -> c != null && c.id() == 101L), anyList())).thenReturn(List.of());

        worker.run(10L);

        assertThat(broken.getProcessingState()).isEqualTo(ProcessingState.FAILED);
        assertThat(broken.getProcessingError()).isEqualTo("model crashed");
        assertThat(fine.getProcessingState()).isEqualTo(ProcessingState.DONE);
        assertThat(run.getProcessedEmails()).isEqualTo(2);
        assertThat(run.getFailedEmails()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
    }

    @Test
    void run_CancelledRunStopsBeforeTheNextEmail() {
        settings(AiPermissionLevel.OFF, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        email(100L);
        email(101L);
        emailsToProcess(100L, 101L);
        when(extractionService.analyze(any(EmailContent.class), anyList())).thenAnswer(inv -> {
            run.setStatus(AnalysisRunStatus.CANCELLED);
            return List.of();
        });

        worker.run(10L);

        verify(extractionService).analyze(argThat(c -> c != null && c.id() == 100L), anyList());
        verify(extractionService, never()).analyze(argThat(c -> c != null && c.id() == 101L), anyList());
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.CANCELLED);
        verify(emailMessageRepository, never()).updateProcessingStateBefore(any(), any(), any(), any());
    }

    @Test
    void run_DiscoveryFailureFailsTheRun() {
        settings(AiPermissionLevel.AUTO, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        when(inboxDiscoveryService.discover(any(), anyList(), any())).thenThrow(new OllamaException("Ollama is not reachable"));

        worker.run(10L);

        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(run.getError()).isEqualTo("Ollama is not reachable");
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
    }

    @Test
    void run_FinishedRunIsIgnored() {
        run.setStatus(AnalysisRunStatus.COMPLETED);

        worker.run(10L);

        verify(aiSettingsService, never()).settingsFor(any());
        verify(extractionService, never()).analyze(any(), anyList());
    }

    @Test
    void processNewMail_HandlesNewEmailsOfOnboardedUsers() {
        when(aiSettingsService.onboardedUsers()).thenReturn(List.of(OWNER));
        settings(AiPermissionLevel.OFF, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        EmailMessage email = email(200L);
        when(emailMessageRepository.findIdsForProcessing(eq(OWNER), eq(ProcessingState.NEW), eq(Instant.EPOCH), any())).thenReturn(List.of(200L));
        when(extractionService.analyze(any(EmailContent.class), anyList())).thenReturn(List.of());

        worker.processNewMail();

        assertThat(email.getProcessingState()).isEqualTo(ProcessingState.DONE);
    }

    @Test
    void processNewMail_SkipsUsersWithAnActiveRun() {
        when(aiSettingsService.onboardedUsers()).thenReturn(List.of(OWNER));
        when(aiAnalysisRunRepository.existsByUser_IdAndStatusIn(OWNER, AnalysisRunStatus.ACTIVE)).thenReturn(true);

        worker.processNewMail();

        verify(emailMessageRepository, never()).findIdsForProcessing(any(), any(), any(), any());
    }
}
