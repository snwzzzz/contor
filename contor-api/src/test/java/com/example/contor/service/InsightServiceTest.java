package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.DigestEmail;
import com.example.contor.ai.Insight;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ProposalKind;
import com.example.contor.persistence.AiAnalysisRunRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.persistence.RecordSourceRepository;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private ProposalService proposalService;

    @Mock
    private CollectionSchemaService collectionSchemaService;

    @Mock
    private RecordService recordService;

    @Mock
    private RecordSourceRepository recordSourceRepository;

    @Mock
    private AiSettingsService aiSettingsService;

    @Mock
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    @Mock
    private InsightModelService insightModelService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private InsightService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 16384, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
        service = new InsightService(emailMessageRepository, proposalRepository, proposalService, collectionSchemaService, recordService,
                recordSourceRepository, aiSettingsService, aiAnalysisRunRepository, insightModelService, properties,
                new AiExecutors(Runnable::run, Runnable::run), transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(collectionSchemaService.findAll()).thenReturn(List.of(new CollectionDto().id(4L).slug("tasks").name("To-dos"),
                new CollectionDto().id(3L).slug("events").name("Calendar")));
        lenient().when(recordService.listRecords(anyLong(), anyList(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new RecordPageDto(new ArrayList<>(), 0, 20, 0L, 0));
        lenient().when(emailMessageRepository.findDigest(eq(OWNER), any(), any())).thenReturn(List.of(
                new DigestEmail(12L, "StreamBox", "billing@streambox.tv", "Invoice", "14.99", NOW.minusSeconds(86_400), "inbox")));
        lenient().when(proposalService.submitDraft(any(), any(), any(), anyString(), anyString(), anyCollection()))
                .thenReturn(new ProposalService.Submission(1L, ProposalService.Outcome.SUGGESTED));
    }

    private void settings(AiPermissionLevel tasks, AiPermissionLevel calendar) {
        when(aiSettingsService.settingsFor(OWNER)).thenReturn(AiSettings.builder().mailAccess(true).tasks(tasks).calendar(calendar).build());
    }

    private void unansweredQuestion() {
        when(emailMessageRepository.findUnansweredSent(eq(OWNER), any(), any(), anyInt())).thenReturn(List.of(42L));
        when(emailMessageRepository.findAllById(List.of(new EmailMessage.EmailMessageId(42L)))).thenReturn(List.of(EmailMessage.builder()
                .id(new EmailMessage.EmailMessageId(42L)).folder("sent").subject("Workshop dates").bodyText("Which day works for you?")
                .toRecipients(List.of(new EmailRecipient("Anna Berger", "anna@acme.com"))).receivedAt(NOW.minus(Duration.ofDays(6))).build()));
    }

    private static Insight modelInsight(String key) {
        return new Insight(key, "StreamBox got more expensive", "14.99 instead of 9.99", "You may not have agreed", List.of(12L, 10L),
                List.of(new Insight.FollowUp("tasks", "Check the plan", Map.of("due_date", "2026-10-15")),
                        new Insight.FollowUp("events", "Call StreamBox", Map.of("starts_at", "2026-10-16T10:00:00+02:00"))),
                "model", new BigDecimal("0.8"));
    }

    @SuppressWarnings("unchecked")
    private List<ProposalDraft> submitted(int times) {
        ArgumentCaptor<ProposalDraft> drafts = ArgumentCaptor.forClass(ProposalDraft.class);
        verify(proposalService, times(times)).submitDraft(drafts.capture(), any(), eq(AiPermissionLevel.SUGGEST), anyString(), anyString(),
                anyCollection());
        return drafts.getAllValues();
    }

    @Test
    @SuppressWarnings("unchecked")
    void runFor_ProposesRuleInsightsFirstThenTheModelsWithTheirFollowUps() {
        settings(AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        unansweredQuestion();
        when(insightModelService.findInsights(anyList(), anyList(), anyList())).thenReturn(List.of(modelInsight("model:10,12")));

        assertThat(service.runFor(OWNER)).isEqualTo(2);

        List<ProposalDraft> drafts = submitted(2);
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.kind()).isEqualTo(ProposalKind.INSIGHT));
        assertThat(drafts.getFirst().payload()).containsEntry("insightKey", "unanswered:42").containsEntry("source", "rule:unanswered-question");
        assertThat((List<Map<String, Object>>) drafts.getFirst().payload().get("followUps")).singleElement()
                .satisfies(followUp -> assertThat(followUp).containsEntry("collectionId", 4L).containsEntry("values", Map.of("due_date", "2026-10-13")));
        assertThat(drafts.get(1).payload()).containsEntry("evidenceEmailIds", List.of(12L, 10L));
        assertThat((List<Map<String, Object>>) drafts.get(1).payload().get("followUps")).extracting(followUp -> followUp.get("collectionId"))
                .containsExactly(4L, 3L);
    }

    @Test
    void runFor_AnInsightProposedBeforeIsNotProposedAgain() {
        settings(AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        unansweredQuestion();
        when(proposalRepository.existsInsightKey("unanswered:42")).thenReturn(true);

        assertThat(service.runFor(OWNER)).isZero();

        verify(proposalService, never()).submitDraft(any(), any(), any(), any(), any(), anyCollection());
    }

    @Test
    @SuppressWarnings("unchecked")
    void runFor_FollowUpsTheAssistantMayNotCreateAreDroppedButTheInsightStays() {
        settings(AiPermissionLevel.OFF, AiPermissionLevel.SUGGEST);
        when(insightModelService.findInsights(anyList(), anyList(), anyList())).thenReturn(List.of(modelInsight("model:10,12")));

        service.runFor(OWNER);

        assertThat((List<Map<String, Object>>) submitted(1).getFirst().payload().get("followUps"))
                .extracting(followUp -> followUp.get("collectionId")).containsExactly(3L);
    }

    @Test
    void runFor_AtMostFiveInsightsPerRun() {
        settings(AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        when(insightModelService.findInsights(anyList(), anyList(), anyList()))
                .thenReturn(IntStream.range(0, 7).mapToObj(i -> modelInsight("model:" + i)).toList());

        assertThat(service.runFor(OWNER)).isEqualTo(5);
    }

    @Test
    void runFor_AFailingModelPassKeepsTheRuleInsights() {
        settings(AiPermissionLevel.SUGGEST, AiPermissionLevel.SUGGEST);
        unansweredQuestion();
        when(insightModelService.findInsights(anyList(), anyList(), anyList())).thenThrow(new IllegalStateException("model down"));

        assertThat(service.runFor(OWNER)).isEqualTo(1);
    }

    @Test
    void runFor_NothingWithoutMailAccessOrWithoutToDosAndCalendar() {
        when(aiSettingsService.settingsFor(OWNER)).thenReturn(AiSettings.builder().mailAccess(false).build());
        assertThat(service.runFor(OWNER)).isZero();

        settings(AiPermissionLevel.OFF, AiPermissionLevel.OFF);
        assertThat(service.runFor(OWNER)).isZero();

        verifyNoInteractions(insightModelService, proposalService);
    }

    @Test
    void runFor_NotWhileAnAnalysisRuns() {
        when(aiAnalysisRunRepository.existsByUser_IdAndStatusIn(eq(OWNER), any())).thenReturn(true);

        assertThat(service.runFor(OWNER)).isZero();

        verifyNoInteractions(insightModelService, proposalService);
    }
}
