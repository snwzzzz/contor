package com.example.contor.service;

import com.example.contor.api.model.*;
import com.example.contor.config.WebProperties;
import com.example.contor.domain.*;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.exception.MailCallbackException;
import com.example.contor.exception.MailAccountNotFoundException;
import com.example.contor.exception.MailAccountPhotoNotFoundException;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.persistence.*;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private MailAccountRepository mailAccountRepository;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private EmailLabelRepository emailLabelRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private MsGraphAuthService msGraphAuthService;

    @Mock
    private MsGraphMailService msGraphMailService;

    @Mock
    private MailSyncService mailSyncService;

    @Mock
    private RecordService recordService;

    @Mock
    private CollectionSchemaService collectionSchemaService;

    @Mock
    private RecordSourceRepository recordSourceRepository;

    @Mock
    private ReplyDraftRepository replyDraftRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private ReplySuggestionService replySuggestionService;

    @Spy
    private WebProperties webProperties = new WebProperties("http://localhost:3000");

    @InjectMocks
    private MailService mailService;

    @BeforeEach
    void login() {
        var principal = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static MailAccount account(long ownerId) {
        return MailAccount.builder().id(new MailAccount.MailAccountId(2L)).owner(AppUser.builder().id(new AppUser.AppUserId(ownerId)).build())
                .provider(MailProvider.MICROSOFT_GRAPH).emailAddress(new Email("me@example.com")).status(MailAccountStatus.ACTIVE).build();
    }

    private static EmailMessage email(long ownerId) {
        return EmailMessage.builder().id(new EmailMessage.EmailMessageId(5L)).account(account(ownerId)).providerMessageId("AAMk")
                .subject("Shipped").receivedAt(Instant.parse("2026-01-01T10:00:00Z")).processingState(ProcessingState.NEW).build();
    }

    private void labelled() {
        when(emailLabelRepository.findById_EmailIdIn(List.of(5L))).thenReturn(List.of(
                EmailLabel.builder().id(new EmailLabel.EmailLabelId(5L, 3L)).source(LabelSource.MANUAL).build()));
        when(labelRepository.findAllById(List.of(new Label.LabelId(3L)))).thenReturn(List.of(
                Label.builder().id(new Label.LabelId(3L)).slug(new Slug("orders")).name("Orders").color(new HexColor("#00ff00")).build()));
    }

    @Test
    void listMailAccounts_OfTheCurrentUser() {
        when(mailAccountRepository.findByOwner_Id(new AppUser.AppUserId(1L))).thenReturn(List.of(account(1L)));

        assertEquals(1, mailService.listMailAccounts().size());
    }

    @Test
    void connectAccount_ReturnsAuthorizeUrl() {
        when(msGraphAuthService.buildAuthorizeUrl(1L, "test@example.com")).thenReturn("https://login.microsoft.com/auth");

        var cmd = new ConnectMailAccountCommand().emailHint("test@example.com");
        var result = mailService.connectAccount(cmd);

        assertEquals("https://login.microsoft.com/auth", result.getAuthorizeUrl());
    }

    @Test
    void handleCallback_SuccessCreatesAccountAndSyncs() {
        when(msGraphAuthService.validateStateAndExtractUserId("state123")).thenReturn(1L);
        when(msGraphAuthService.exchangeCode("code123"))
                .thenReturn(new MsGraphAuthService.TokenResult("access1", "refresh1", Instant.now().plusSeconds(3600)));
        when(msGraphMailService.fetchUserProfile("access1"))
                .thenReturn(new MsGraphMailService.UserProfile("test@outlook.com", "Test User"));
        var user = AppUser.builder().id(new AppUser.AppUserId(1L)).build();
        when(appUserRepository.findById(new AppUser.AppUserId(1L))).thenReturn(Optional.of(user));
        when(mailAccountRepository.findByOwner_IdAndEmailAddress(eq(new AppUser.AppUserId(1L)), any(Email.class)))
                .thenReturn(Optional.empty());

        String next = mailService.handleCallback("state123", "code123", null, null);

        assertEquals("http://localhost:3000/settings/accounts?connected=true", next);
        verify(mailAccountRepository).saveAndFlush(any(MailAccount.class));
        verify(mailSyncService).syncAccount(any(MailAccount.class));
    }

    @Test
    void handleCallback_CancelledSignInForwardsWithError() {
        var ex = assertThrows(MailCallbackException.class,
                () -> mailService.handleCallback("state123", null, "access_denied", "User canceled"));

        assertEquals("http://localhost:3000/settings/accounts?mailError=Microsoft+sign-in+was+cancelled.", ex.getForwardUrl());
        verify(mailAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void handleCallback_MissingStateForwardsWithError() {
        var ex = assertThrows(MailCallbackException.class, () -> mailService.handleCallback(null, "code123", null, null));

        assertTrue(ex.getForwardUrl().startsWith("http://localhost:3000/settings/accounts?mailError="));
        verify(msGraphAuthService, never()).exchangeCode(any());
    }

    @Test
    void handleCallback_TokenExchangeFailureForwardsWithError() {
        when(msGraphAuthService.validateStateAndExtractUserId("state123")).thenReturn(1L);
        when(msGraphAuthService.exchangeCode("code123")).thenThrow(new IllegalStateException("AADSTS70008 internal detail"));

        var ex = assertThrows(MailCallbackException.class, () -> mailService.handleCallback("state123", "code123", null, null));

        assertEquals("http://localhost:3000/settings/accounts?mailError=Connecting+the+mailbox+failed.+Please+try+again.",
                ex.getForwardUrl());
        verify(mailAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void syncAccount_Success() {
        var acc = account(1L);
        when(mailAccountRepository.findById(new MailAccount.MailAccountId(2L))).thenReturn(Optional.of(acc));
        when(mailSyncService.syncAccount(acc)).thenReturn(new MailSyncStatusDto(2L, MailAccountState.ACTIVE));

        var status = mailService.syncAccount(2L);
        assertEquals(MailAccountState.ACTIVE, status.getStatus());
    }

    @Test
    void syncAccount_NotFoundThrows() {
        when(mailAccountRepository.findById(new MailAccount.MailAccountId(99L))).thenReturn(Optional.empty());

        assertThrows(MailAccountNotFoundException.class, () -> mailService.syncAccount(99L));
    }

    @Test
    void disconnectAccount_Success() {
        var acc = account(1L);
        acc.setDeltaLink("inbox-delta");
        acc.setSentDeltaLink("sent-delta");
        when(mailAccountRepository.findById(new MailAccount.MailAccountId(2L))).thenReturn(Optional.of(acc));

        mailService.disconnectAccount(2L);

        assertNull(acc.getDeltaLink());
        assertNull(acc.getSentDeltaLink());

        assertEquals(MailAccountStatus.DISABLED, acc.getStatus());
        assertNull(acc.getAccessToken());
        assertNull(acc.getRefreshToken());
        verify(mailAccountRepository).saveAndFlush(acc);
    }

    @Test
    void getSyncStatus_Success() {
        var acc = account(1L);
        acc.setLastSyncedAt(Instant.parse("2026-03-01T12:00:00Z"));
        when(mailAccountRepository.findById(new MailAccount.MailAccountId(2L))).thenReturn(Optional.of(acc));

        var status = mailService.getSyncStatus(2L);

        assertEquals(2L, status.getAccountId());
        assertEquals(MailAccountState.ACTIVE, status.getStatus());
        assertNotNull(status.getLastSyncedAt());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listEmails_NewestFirstWithLabels() {
        var pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "receivedAt"));
        when(emailMessageRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(email(1L)), pageable, 1));
        labelled();

        EmailPageDto page = mailService.listEmails(null, null, null, null, null, null, null, null, null);

        assertEquals(1L, page.getTotalElements());
        assertEquals("Shipped", page.getItems().get(0).getSubject());
        assertEquals("orders", page.getItems().get(0).getLabels().get(0).getSlug());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listEmails_SentFolderNeverCountsAsAnswered() {
        var pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "receivedAt"));
        var sent = email(1L);
        sent.setFolder("sent");
        sent.setConversationId("conv-1");
        when(emailMessageRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(sent), pageable, 1));
        when(emailMessageRepository.threadStats(new AppUser.AppUserId(1L), List.of("conv-1"))).thenReturn(List.of(
                new ThreadStat(2L, "conv-1", 3L, Instant.parse("2026-01-09T10:00:00Z"))));
        labelled();

        EmailSummaryDto summary = mailService.listEmails(null, null, null, null, null, null, EmailFolder.SENT, null, null).getItems().get(0);

        assertEquals("sent", summary.getFolder());
        assertEquals(3, summary.getThreadSize());
        assertFalse(summary.getAnswered());
    }

    @Test
    void suggestEmailSenders_SentFolderSuggestsRecipients() {
        when(emailMessageRepository.findRecipients(new AppUser.AppUserId(1L), "%an%", 50)).thenReturn(List.of(
                new SenderCount("Anna", "anna@acme.com", 4L),
                new SenderCount(null, "dan@gmail.com", 1L)));

        List<EmailSuggestionDto> suggestions = mailService.suggestEmailSenders("an", EmailFolder.SENT);

        assertEquals(List.of("Anna", "dan@gmail.com"), suggestions.stream().map(EmailSuggestionDto::getLabel).toList());
        verify(emailMessageRepository, never()).findSenders(any(), any(), any());
    }

    @Test
    void suggestEmailSenders_PeopleThenCompaniesWithoutFreeMail() {
        when(emailMessageRepository.findSenders(eq(new AppUser.AppUserId(1L)), eq("%a%"), any(Pageable.class))).thenReturn(List.of(
                new SenderCount("Anna", "anna@acme.com", 5L),
                new SenderCount(null, "dan@gmail.com", 3L),
                new SenderCount("Anton", "anton@acme.com", 2L),
                new SenderCount("Hans", "hans@hans-gmbh.at", 1L)));

        List<EmailSuggestionDto> suggestions = mailService.suggestEmailSenders(" A ", null);

        assertEquals(List.of("Anna", "dan@gmail.com", "Anton", "Hans", "acme.com", "hans-gmbh.at"),
                suggestions.stream().map(EmailSuggestionDto::getLabel).toList());
        var company = suggestions.get(4);
        assertEquals(EmailSuggestionKind.COMPANY, company.getKind());
        assertEquals("@acme.com", company.getValue());
        assertEquals(7L, company.getEmailCount());
        assertEquals("anna@acme.com", suggestions.get(0).getValue());
    }

    @Test
    void suggestEmailSenders_CompanyOnlyWhenTheDomainMatches() {
        when(emailMessageRepository.findSenders(any(), eq("%anna%"), any(Pageable.class)))
                .thenReturn(List.of(new SenderCount("Anna", "anna@acme.com", 5L)));

        assertEquals(List.of("Anna"), mailService.suggestEmailSenders("anna", null).stream().map(EmailSuggestionDto::getLabel).toList());
    }

    @Test
    void listRecordEmails_MatchesSourcesAddressesAndDomains() {
        when(collectionSchemaService.findById(1L)).thenReturn(new CollectionDto().id(1L).fields(List.of(
                new CollectionFieldDto(1L, "email", "Email", CollectionFieldType.EMAIL, new LinkedHashMap<>(), false, false, 1))));
        when(recordService.getRecord(1L, 5L)).thenReturn(new RecordDto(5L, 1L, new HashMap<>(Map.of("email", "anna@acme.com")),
                new HashMap<>(), OffsetDateTime.parse("2026-10-01T00:00:00Z"), "AI"));
        when(recordSourceRepository.findByCollection_IdAndRecordId(new Collection.CollectionId(1L), 5L)).thenReturn(List.of(
                RecordSource.builder().email(EmailMessage.builder().id(new EmailMessage.EmailMessageId(31L)).build()).build()));
        var pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "receivedAt"));
        when(emailMessageRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(email(1L)), pageable, 1));
        labelled();

        EmailPageDto page = mailService.listRecordEmails(1L, 5L, null, null);

        assertEquals(1L, page.getTotalElements());
        assertEquals("Shipped", page.getItems().get(0).getSubject());
    }

    @Test
    void listRecordEmails_NothingToMatchQueriesNothing() {
        when(collectionSchemaService.findById(1L)).thenReturn(new CollectionDto().id(1L).fields(List.of()));
        when(recordService.getRecord(1L, 5L)).thenReturn(new RecordDto(5L, 1L, new HashMap<>(), new HashMap<>(),
                OffsetDateTime.parse("2026-10-01T00:00:00Z"), "AI"));
        when(recordSourceRepository.findByCollection_IdAndRecordId(new Collection.CollectionId(1L), 5L)).thenReturn(List.of());

        EmailPageDto page = mailService.listRecordEmails(1L, 5L, 0, 10);

        assertEquals(0L, page.getTotalElements());
        verify(emailMessageRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listRecordEmails_UnknownRecord() {
        when(recordService.getRecord(1L, 5L)).thenThrow(new RecordNotFoundException(1L, 5L));

        assertThrows(RecordNotFoundException.class, () -> mailService.listRecordEmails(1L, 5L, null, null));
    }

    @Test
    void getEmail() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.of(email(1L)));
        labelled();

        EmailDto email = mailService.getEmail(5L);

        assertEquals("Shipped", email.getSubject());
        assertEquals(1, email.getLabels().size());
    }

    @Test
    void getEmail_OfAnotherUserIsNotFound() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.of(email(2L)));

        assertThrows(EmailNotFoundException.class, () -> mailService.getEmail(5L));
    }

    @Test
    void getEmailThread_OldestFirstIncludingSentMail() {
        var anchor = email(1L);
        anchor.setConversationId("conv-1");
        var reply = EmailMessage.builder().id(new EmailMessage.EmailMessageId(6L)).account(account(1L)).providerMessageId("AAMl")
                .folder("sent").subject("Re: Shipped").receivedAt(Instant.parse("2026-01-02T10:00:00Z")).build();
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.of(anchor));
        when(emailMessageRepository.findThread(new MailAccount.MailAccountId(2L), "conv-1")).thenReturn(List.of(anchor, reply));

        EmailThreadDto thread = mailService.getEmailThread(5L);

        assertEquals("conv-1", thread.getConversationId());
        assertEquals(List.of(5L, 6L), thread.getMessages().stream().map(EmailDto::getId).toList());
        assertEquals("sent", thread.getMessages().get(1).getFolder());
    }

    @Test
    void getEmailThread_CarriesKeptDraftsThePendingSuggestionAndItsState() {
        var anchor = email(1L);
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.of(anchor));
        var draft = ReplyDraft.builder().id(new ReplyDraft.ReplyDraftId(4L)).email(anchor).account(anchor.getAccount())
                .subject("Re: Shipped").body("Thanks").status(ReplyDraftStatus.READY).build();
        when(replyDraftRepository.findByEmail_IdInAndStatusOrderByCreatedAtAsc(List.of(anchor.getId()), ReplyDraftStatus.READY))
                .thenReturn(List.of(draft));
        var older = Proposal.builder().id(new Proposal.ProposalId(10L)).kind(ProposalKind.DRAFT_REPLY).payload(Map.of("body", "a")).build();
        var newer = Proposal.builder().id(new Proposal.ProposalId(11L)).kind(ProposalKind.DRAFT_REPLY).payload(Map.of("body", "b")).build();
        when(proposalRepository.findByKindAndStatusAndSourceEmail_IdIn(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, List.of(anchor.getId())))
                .thenReturn(List.of(older, newer));
        when(replySuggestionService.state(5L)).thenReturn(ReplySuggestionState.RUNNING);

        EmailThreadDto thread = mailService.getEmailThread(5L);

        assertEquals(List.of(4L), thread.getReplyDrafts().stream().map(ReplyDraftDto::getId).toList());
        assertEquals(11L, thread.getPendingReplyProposal().getId());
        assertEquals(ReplySuggestionState.RUNNING, thread.getReplySuggestion());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listEmails_MarksSuggestedAndKeptReplies() {
        var pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "receivedAt"));
        var suggested = email(1L);
        var kept = EmailMessage.builder().id(new EmailMessage.EmailMessageId(6L)).account(account(1L)).providerMessageId("AAMl")
                .subject("Other").receivedAt(Instant.parse("2026-01-02T10:00:00Z")).build();
        var plain = EmailMessage.builder().id(new EmailMessage.EmailMessageId(7L)).account(account(1L)).providerMessageId("AAMm")
                .subject("Plain").receivedAt(Instant.parse("2026-01-03T10:00:00Z")).build();
        when(emailMessageRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(suggested, kept, plain), pageable, 3));
        when(proposalRepository.findByKindAndStatusAndSourceEmail_IdIn(eq(ProposalKind.DRAFT_REPLY), eq(ProposalStatus.PENDING), any()))
                .thenReturn(List.of(Proposal.builder().sourceEmail(suggested).build()));
        when(replyDraftRepository.findEmailIdsWithStatus(List.of(5L, 6L, 7L), ReplyDraftStatus.READY)).thenReturn(List.of(6L));

        List<EmailSummaryDto> items = mailService.listEmails(null, null, null, null, null, null, null, null, null).getItems();

        assertEquals(ReplyMarker.SUGGESTED, items.get(0).getReplyState());
        assertEquals(ReplyMarker.DRAFT_READY, items.get(1).getReplyState());
        assertNull(items.get(2).getReplyState());
    }

    @Test
    void getEmailThread_WithoutConversationIsJustTheEmail() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.of(email(1L)));

        EmailThreadDto thread = mailService.getEmailThread(5L);

        assertEquals(List.of(5L), thread.getMessages().stream().map(EmailDto::getId).toList());
        verify(emailMessageRepository, never()).findThread(any(), any());
    }

    @Test
    void getEmailThread_OfAnotherUserIsNotFound() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.of(email(2L)));

        assertThrows(EmailNotFoundException.class, () -> mailService.getEmailThread(5L));
        verify(emailMessageRepository, never()).findThread(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listEmails_ShowsThreadSizeAndWhetherTheUserAnswered() {
        var pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "receivedAt"));
        var threaded = email(1L);
        threaded.setConversationId("conv-1");
        when(emailMessageRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(threaded), pageable, 1));
        when(emailMessageRepository.threadStats(new AppUser.AppUserId(1L), List.of("conv-1"))).thenReturn(List.of(
                new ThreadStat(2L, "conv-1", 3L, Instant.parse("2026-01-02T10:00:00Z"))));
        labelled();

        EmailSummaryDto summary = mailService.listEmails(null, null, null, null, null, null, null, null, null).getItems().get(0);

        assertEquals(3, summary.getThreadSize());
        assertTrue(summary.getAnswered());
    }

    @Test
    void getEmail_NotFound() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.empty());

        assertThrows(EmailNotFoundException.class, () -> mailService.getEmail(5L));
    }

    @Test
    void getPhoto_ReturnsStoredPhoto() {
        var acc = account(1L);
        acc.setPhoto(new byte[] {1, 2, 3});
        acc.setPhotoContentType("image/png");
        when(mailAccountRepository.findById(new MailAccount.MailAccountId(2L))).thenReturn(Optional.of(acc));

        var photo = mailService.getPhoto(2L);

        assertArrayEquals(new byte[] {1, 2, 3}, photo.bytes());
        assertEquals("image/png", photo.contentType());
    }

    @Test
    void getPhoto_NotFoundWithoutPhoto() {
        when(mailAccountRepository.findById(new MailAccount.MailAccountId(2L))).thenReturn(Optional.of(account(1L)));

        assertThrows(MailAccountPhotoNotFoundException.class, () -> mailService.getPhoto(2L));
    }

    @Test
    void getPhoto_NotFoundForForeignAccount() {
        var foreign = account(99L);
        foreign.setPhoto(new byte[] {1});
        when(mailAccountRepository.findById(new MailAccount.MailAccountId(2L))).thenReturn(Optional.of(foreign));

        assertThrows(MailAccountNotFoundException.class, () -> mailService.getPhoto(2L));
    }

    @Test
    void handleCallback_ReconnectFetchesThePhotoAgain() {
        var existing = account(1L);
        existing.setPhotoCheckedAt(Instant.now());
        when(msGraphAuthService.validateStateAndExtractUserId("state123")).thenReturn(1L);
        when(msGraphAuthService.exchangeCode("code123"))
                .thenReturn(new MsGraphAuthService.TokenResult("access1", "refresh1", Instant.now().plusSeconds(3600)));
        when(msGraphMailService.fetchUserProfile("access1")).thenReturn(new MsGraphMailService.UserProfile("me@example.com", "Me"));
        when(appUserRepository.findById(new AppUser.AppUserId(1L))).thenReturn(Optional.of(existing.getOwner()));
        when(mailAccountRepository.findByOwner_IdAndEmailAddress(eq(new AppUser.AppUserId(1L)), any(Email.class)))
                .thenReturn(Optional.of(existing));

        mailService.handleCallback("state123", "code123", null, null);

        assertNull(existing.getPhotoCheckedAt());
    }
}
