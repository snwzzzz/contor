package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.ImportanceSignals;
import com.example.contor.ai.SlotFinder;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.api.model.ReplySuggestionState;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.exception.ReplySuggestionUnavailableException;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplySuggestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final String TRIAGE_YES = "{\"needsReply\": true, \"reason\": \"Anna asks for a date\", \"urgency\": \"normal\"}";
    private static final String DRAFT = """
            {"subject": "Re: Workshop", "body": "Hallo Anna,\\n\\nich kann am [Tag] kommen.\\n\\nMax", "language": "de",
             "placeholders": ["[Tag]"], "summary": "Proposes a day"}""";

    @Mock
    private LlmClient llmClient;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private MailAccountRepository mailAccountRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private ProposalService proposalService;

    @Mock
    private ReplyLifecycleService replyLifecycleService;

    @Mock
    private CollectionSchemaService collectionSchemaService;

    @Mock
    private RecordService recordService;

    @Mock
    private AiSettingsService aiSettingsService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ImportanceSignalService importanceSignalService;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private MailContextService mailContextService;

    private ReplySuggestionService service;
    private MailAccount account;
    private final List<EmailMessage> conversation = new ArrayList<>();

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JsonMapper mapper = JsonMapper.builder().build();
        service = new ReplySuggestionService(emailMessageRepository, mailAccountRepository, proposalRepository, proposalService,
                replyLifecycleService, new ReplyTriageService(llmClient, properties, mapper),
                new ReplyComposerService(llmClient, properties, mapper, clock), collectionSchemaService, recordService,
                aiSettingsService, importanceSignalService, availabilityService, mailContextService, properties,
                new AiExecutors(Runnable::run, Runnable::run), transactionManager, clock);
        lenient().when(importanceSignalService.signalsFor(any())).thenReturn(new ImportanceSignals(null, true, false, false));

        AppUser owner = AppUser.builder().id(new AppUser.AppUserId(1L)).build();
        account = MailAccount.builder().id(new MailAccount.MailAccountId(3L)).owner(owner).displayName("Max Muster")
                .emailAddress(new Email("me@example.com")).build();
        MailAccount second = MailAccount.builder().id(new MailAccount.MailAccountId(4L)).owner(owner)
                .emailAddress(new Email("max@work.example")).build();
        lenient().when(mailAccountRepository.findByOwner_Id(owner.getId())).thenReturn(List.of(account, second));
        lenient().when(emailMessageRepository.findThread(account.getId(), "conv")).thenReturn(conversation);
        lenient().when(collectionSchemaService.findAll()).thenReturn(List.of());
        lenient().when(aiSettingsService.settingsFor(owner.getId())).thenReturn(AiSettings.builder().mailAccess(true)
                .replies(AiPermissionLevel.SUGGEST).build());
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static void login(long userId) {
        var principal = new AuthenticatedUser(userId, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private EmailMessage message(long id, String folder, String from, String fromName, String body, Instant at) {
        EmailMessage email = EmailMessage.builder().id(new EmailMessage.EmailMessageId(id)).account(account).conversationId("conv")
                .folder(folder).fromAddress(from).fromName(fromName)
                .toRecipients(List.of(new EmailRecipient("Max", "me@example.com")))
                .ccRecipients(List.of(new EmailRecipient("Bob", "bob@acme.com"), new EmailRecipient("Me", "MAX@work.example")))
                .subject("Workshop").bodyText(body).receivedAt(at).build();
        conversation.add(email);
        lenient().when(emailMessageRepository.findById(email.getId())).thenReturn(Optional.of(email));
        return email;
    }

    private EmailMessage question() {
        message(7L, "sent", "me@example.com", "Max", "Hallo Anna, wann passt es dir?", NOW.minus(Duration.ofDays(3)));
        return message(8L, "inbox", "anna@acme.com", "Anna Berger", "Hallo Max, an welchem Tag kannst du kommen?",
                NOW.minus(Duration.ofDays(1)));
    }

    private void llm(String triage, String draft) {
        lenient().when(llmClient.completeJson(eq("router"), anyString(), anyString(), any())).thenReturn(triage);
        lenient().when(llmClient.completeJson(eq("extractor"), anyString(), anyString(), any())).thenReturn(draft);
    }

    @SuppressWarnings("unchecked")
    private ProposalDraft submitted() {
        ArgumentCaptor<ProposalDraft> draft = ArgumentCaptor.forClass(ProposalDraft.class);
        verify(proposalService).submitDraft(draft.capture(), eq(8L), eq(AiPermissionLevel.SUGGEST), eq("extractor"),
                eq(ReplyComposerService.PROMPT_VERSION), anyCollection());
        return draft.getValue();
    }

    @Test
    void suggestIfNeeded_TheDraftCarriesTheSendersSignalsAndTheTriageUrgency() {
        question();
        llm("{\"needsReply\": true, \"reason\": \"Anna asks for a date\", \"urgency\": \"high\"}", DRAFT);

        service.suggestIfNeeded(8L);

        assertThat(submitted().signals()).isEqualTo(new ImportanceSignals("high", true, false, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestIfNeeded_AMeetingRequestOffersFreeTimesAndListsTheQuestions() {
        question();
        llm("""
                {"needsReply": true, "reason": "Anna asks for a day", "urgency": "normal", "intent": "SCHEDULE",
                 "questions": ["An welchem Tag kannst du kommen?"], "scheduleFrom": "2026-10-12", "scheduleTo": "2026-10-16"}""", """
                {"subject": "Re: Workshop", "body": "Hallo Anna, Dienstag um 11 Uhr passt. Max", "language": "de", "placeholders": [],
                 "summary": "Offers Tuesday", "answers": [{"question": "An welchem Tag kannst du kommen?", "answered": true}]}""");
        ZoneId zone = ZoneId.of("Europe/Vienna");
        when(availabilityService.freeSlots(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16), SlotFinder.TimeOfDay.ANY)).thenReturn(List.of(
                new SlotFinder.Slot(ZonedDateTime.of(2026, 10, 13, 11, 0, 0, 0, zone), ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, zone))));
        when(mailContextService.contextTextWithoutThread(8L)).thenReturn("Earlier emails, for context only: CONTEXT-MARKER");

        service.suggestIfNeeded(8L);

        Map<String, Object> payload = submitted().payload();
        assertThat(payload.get("intent")).isEqualTo("SCHEDULE");
        assertThat((List<Map<String, Object>>) payload.get("questions"))
                .containsExactly(Map.of("question", "An welchem Tag kannst du kommen?", "answered", true));
        assertThat((List<Map<String, Object>>) payload.get("proposedSlots"))
                .containsExactly(Map.of("start", "2026-10-13T11:00:00+02:00", "end", "2026-10-13T12:00:00+02:00"));
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), anyString(), prompt.capture(), any());
        assertThat(prompt.getValue()).contains("Tuesday, 13 October 2026, 11:00–12:00").contains("CONTEXT-MARKER");
    }

    @Test
    void suggestIfNeeded_AFullyBookedWeekFallsBackToTheNextFreeDays() {
        question();
        llm("""
                {"needsReply": true, "reason": "r", "urgency": "normal", "intent": "SCHEDULE", "questions": [],
                 "scheduleFrom": "2026-10-12", "scheduleTo": "2026-10-16"}""", DRAFT);
        when(availabilityService.freeSlots(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16), SlotFinder.TimeOfDay.ANY)).thenReturn(List.of());

        service.suggestIfNeeded(8L);

        verify(availabilityService).freeSlots(null, null, SlotFinder.TimeOfDay.ANY);
    }

    @Test
    void suggestIfNeeded_AQuestionAboutDatesGetsFreeTimesWhateverTheIntent() {
        question();
        llm("{\"needsReply\": true, \"reason\": \"r\", \"urgency\": \"normal\", \"intent\": \"PROVIDE_INFO\", "
                + "\"scheduleFrom\": \"2026-11-02\", \"scheduleTo\": \"2026-11-30\", \"timeOfDay\": \"EVENING\"}", DRAFT);

        service.suggestIfNeeded(8L);

        verify(availabilityService).freeSlots(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 30), SlotFinder.TimeOfDay.EVENING);
    }

    @Test
    void suggestIfNeeded_NoFreeTimesUnlessTheReplyIsAboutScheduling() {
        question();
        llm(TRIAGE_YES, DRAFT);

        service.suggestIfNeeded(8L);

        verifyNoInteractions(availabilityService);
        assertThat(submitted().payload()).doesNotContainKey("proposedSlots");
    }

    @Test
    void suggestIfNeeded_QuestionGetsAPendingDraft() {
        question();
        llm(TRIAGE_YES, DRAFT);

        assertThat(service.suggestIfNeeded(8L)).isTrue();

        ProposalDraft draft = submitted();
        assertThat(draft.kind()).isEqualTo(ProposalKind.DRAFT_REPLY);
        assertThat(draft.reasoning()).isEqualTo("Proposes a day");
        Map<String, Object> payload = draft.payload();
        assertThat(payload).containsEntry("emailId", 8L).containsEntry("accountId", 3L).containsEntry("conversationId", "conv")
                .containsEntry("subject", "Re: Workshop").containsEntry("language", "de")
                .containsEntry("placeholders", List.of("[Tag]"));
        assertThat(payload.get("to")).isEqualTo(List.of(Map.of("name", "Anna Berger", "address", "anna@acme.com")));
        assertThat(payload.get("cc")).isEqualTo(List.of(Map.of("name", "Bob", "address", "bob@acme.com")));
        assertThat((String) payload.get("body")).contains("[Tag]");
        verify(replyLifecycleService).supersedeConversation(account.getId(), "conv", ReplyLifecycleService.REPLACED);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), anyString(), prompt.capture(), any());
        assertThat(prompt.getValue()).contains("From: You", "wann passt es dir?", "ANSWER THIS: From: Anna Berger <anna@acme.com>");
    }

    @Test
    void suggestIfNeeded_AutomatedSenderMakesNoModelCall() {
        message(8L, "inbox", "noreply@shop.com", "Shop", "Your parcel is on its way", NOW.minus(Duration.ofDays(1)));

        assertThat(service.suggestIfNeeded(8L)).isFalse();

        verifyNoInteractions(llmClient, proposalService);
    }

    @Test
    void suggestIfNeeded_AnsweredConversationMakesNoModelCall() {
        message(8L, "inbox", "anna@acme.com", "Anna", "Question?", NOW.minus(Duration.ofDays(2)));
        message(9L, "sent", "me@example.com", "Max", "Answer", NOW.minus(Duration.ofDays(1)));

        assertThat(service.suggestIfNeeded(8L)).isFalse();

        verifyNoInteractions(llmClient, proposalService);
    }

    @Test
    void suggestIfNeeded_PendingDraftForTheEmailIsKept() {
        question();
        when(proposalRepository.findByKindAndStatusAndSourceEmail_IdIn(eq(ProposalKind.DRAFT_REPLY), eq(ProposalStatus.PENDING), anyCollection()))
                .thenReturn(List.of(Proposal.builder().build()));

        assertThat(service.suggestIfNeeded(8L)).isFalse();

        verifyNoInteractions(llmClient, proposalService);
    }

    @Test
    void suggestIfNeeded_TriageSaysNoReplyNeeded() {
        question();
        llm("{\"needsReply\": false, \"reason\": \"FYI\", \"urgency\": \"low\"}", DRAFT);

        assertThat(service.suggestIfNeeded(8L)).isFalse();

        verify(llmClient, times(1)).completeJson(anyString(), anyString(), anyString(), any());
        verify(proposalService, never()).submitDraft(any(), any(), any(), any(), any(), anyCollection());
    }

    @Test
    void suggestIfNeeded_UnusableDraftCreatesNoProposal() {
        question();
        llm(TRIAGE_YES, "{\"subject\": \"\", \"body\": \"\", \"language\": \"de\", \"placeholders\": [], \"summary\": \"\"}");

        assertThat(service.suggestIfNeeded(8L)).isFalse();

        verify(proposalService, never()).submitDraft(any(), any(), any(), any(), any(), anyCollection());
    }

    @Test
    void suggestIfNeeded_GroundsTheDraftInTheSendersRecords() {
        question();
        llm(TRIAGE_YES, DRAFT);
        when(collectionSchemaService.findAll()).thenReturn(List.of(new CollectionDto().id(1L).slug("people"),
                new CollectionDto().id(3L).slug("events")));
        when(recordService.listRecords(eq(1L), eq(List.of("email:eq:anna@acme.com")), isNull(), isNull(), eq(0), eq(1)))
                .thenReturn(page(new RecordDto().id(5L).title("Anna Berger").values(Map.of("role_title", "Trainer"))));
        when(recordService.listRecords(eq(3L), anyList(), eq("Anna Berger"), eq("starts_at"), eq(0), anyInt()))
                .thenReturn(page(new RecordDto().id(9L).title("Workshop at Acme").values(Map.of("starts_at", "2026-10-20T09:00:00+02:00"))));
        lenient().when(recordService.listRecords(eq(3L), anyList(), eq("Workshop"), eq("starts_at"), eq(0), anyInt()))
                .thenReturn(page(new RecordDto().id(9L).title("Workshop at Acme").values(Map.of())));

        service.suggestIfNeeded(8L);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeJson(eq("extractor"), anyString(), prompt.capture(), any());
        assertThat(prompt.getValue())
                .contains("- The sender in your contacts: Anna Berger, Trainer")
                .contains("- Upcoming appointment: Workshop at Acme – 2026-10-20T09:00:00+02:00");
        assertThat(prompt.getValue().split("Workshop at Acme", -1)).hasSize(2);
    }

    private static RecordPageDto page(RecordDto... records) {
        return new RecordPageDto(new ArrayList<>(List.of(records)), 0, records.length, (long) records.length, 1);
    }

    @Test
    void requestSuggestion_DraftsEvenWhenTheTriageSeesNoNeedToReply() {
        login(1L);
        question();
        llm("{\"needsReply\": false, \"reason\": \"FYI\", \"urgency\": \"low\", \"intent\": \"NONE\"}", DRAFT);

        service.requestSuggestion(8L);

        assertThat(submitted().kind()).isEqualTo(ProposalKind.DRAFT_REPLY);
        assertThat(service.state(8L)).isEqualTo(ReplySuggestionState.IDLE);
    }

    @Test
    void requestSuggestion_FailedDraftIsReported() {
        login(1L);
        question();
        when(llmClient.completeJson(eq("extractor"), anyString(), anyString(), any())).thenThrow(new IllegalStateException("down"));

        service.requestSuggestion(8L);

        assertThat(service.state(8L)).isEqualTo(ReplySuggestionState.FAILED);
    }

    @Test
    void requestSuggestion_NotOwner_NotFound() {
        login(2L);
        question();

        assertThatThrownBy(() -> service.requestSuggestion(8L)).isInstanceOf(EmailNotFoundException.class);
        verifyNoInteractions(llmClient);
    }

    @Test
    void requestSuggestion_RepliesOff_Conflict() {
        login(1L);
        question();
        when(aiSettingsService.settingsFor(new AppUser.AppUserId(1L))).thenReturn(AiSettings.builder().mailAccess(true)
                .replies(AiPermissionLevel.OFF).build());

        assertThatThrownBy(() -> service.requestSuggestion(8L)).isInstanceOf(ReplySuggestionUnavailableException.class);
        verifyNoInteractions(llmClient);
    }

    @Test
    void requestSuggestion_OwnSentMessage_Conflict() {
        login(1L);
        question();

        assertThatThrownBy(() -> service.requestSuggestion(7L)).isInstanceOf(ReplySuggestionUnavailableException.class);
    }

    @Test
    void state_IdleWithoutRequest() {
        assertThat(service.state(99L)).isEqualTo(ReplySuggestionState.IDLE);
    }
}
