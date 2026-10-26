package com.example.contor.service;

import com.example.contor.api.model.MailAccountState;
import com.example.contor.domain.*;
import com.example.contor.exception.SyncCursorExpiredException;
import com.example.contor.mailprovider.ContactPhotoData;
import com.example.contor.mailprovider.FolderChanges;
import com.example.contor.mailprovider.MailOAuthClient;
import com.example.contor.mailprovider.MailProviderRegistry;
import com.example.contor.mailprovider.MailboxClient;
import com.example.contor.mailprovider.OAuthTokens;
import com.example.contor.mailprovider.SyncedMessage;
import com.example.contor.persistence.ContactPhotoRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailSyncServiceTest {

    @Mock
    private MailAccountRepository mailAccountRepository;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private MailProviderRegistry mailProviderRegistry;

    @Mock
    private MailOAuthClient oauthClient;

    @Mock
    private MailboxClient mailboxClient;

    @Mock
    private ContactPhotoRepository contactPhotoRepository;

    @Mock
    private ReplyLifecycleService replyLifecycleService;

    @InjectMocks
    private MailSyncService mailSyncService;

    private MailAccount account;

    @BeforeEach
    void setUp() {
        account = MailAccount.builder()
                .id(new MailAccount.MailAccountId(1L))
                .owner(AppUser.builder().id(new AppUser.AppUserId(10L)).build())
                .provider(MailProvider.MICROSOFT_GRAPH)
                .emailAddress(new Email("user@example.com"))
                .accessToken("valid-token")
                .refreshToken("valid-refresh")
                .tokenExpiresAt(Instant.now().plusSeconds(3600))
                .status(MailAccountStatus.ACTIVE)
                .build();
        lenient().when(mailProviderRegistry.oauth(any())).thenReturn(oauthClient);
        lenient().when(mailProviderRegistry.mailbox(any())).thenReturn(mailboxClient);
    }

    private static FolderChanges last(String syncCursor, SyncedMessage... messages) {
        return new FolderChanges(List.of(messages), null, syncCursor);
    }

    private void emptyInbox() {
        when(mailboxClient.fetchChanges(eq("valid-token"), eq(SyncFolder.INBOX), any())).thenReturn(last("inbox-cursor"));
    }

    private void emptyFolders() {
        when(mailboxClient.fetchChanges(eq("valid-token"), any(), any())).thenReturn(last("cursor"));
    }

    @Test
    void syncAccount_RefreshesTokenWhenExpired() {
        account.setTokenExpiresAt(Instant.now().minusSeconds(10));
        when(oauthClient.refresh("valid-refresh")).thenReturn(new OAuthTokens("new-access", "new-refresh", Instant.now().plusSeconds(3600)));
        when(mailboxClient.fetchChanges(eq("new-access"), any(), any())).thenReturn(last("cursor-1"));

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(account.getAccessToken()).isEqualTo("new-access");
        assertThat(account.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(account.getSyncCursor()).isEqualTo("cursor-1");
        verify(mailAccountRepository, atLeastOnce()).saveAndFlush(account);
    }

    @Test
    void syncAccount_RefreshWithoutNewRefreshTokenKeepsTheOldOne() {
        account.setProvider(MailProvider.GMAIL);
        account.setTokenExpiresAt(Instant.now().minusSeconds(10));
        when(oauthClient.refresh("valid-refresh")).thenReturn(new OAuthTokens("new-access", null, Instant.now().plusSeconds(3600)));
        when(mailboxClient.fetchChanges(eq("new-access"), any(), any())).thenReturn(last("500"));

        mailSyncService.syncAccount(account);

        verify(mailProviderRegistry).oauth(MailProvider.GMAIL);
        assertThat(account.getRefreshToken()).isEqualTo("valid-refresh");
    }

    @Test
    void syncAccount_MarksNeedsReauthOnRefreshFailure() {
        account.setTokenExpiresAt(Instant.now().minusSeconds(10));
        when(oauthClient.refresh("valid-refresh")).thenThrow(new IllegalStateException("Invalid refresh token"));

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.NEEDS_REAUTH);
        assertThat(account.getStatus()).isEqualTo(MailAccountStatus.NEEDS_REAUTH);
        verify(mailAccountRepository).saveAndFlush(account);
        verifyNoInteractions(mailboxClient);
    }

    @Test
    void syncAccount_PaginatesAndSavesMessages() {
        var message = SyncedMessage.builder().providerMessageId("m1").subject("Hello").receivedAt(Instant.now())
                .from(new EmailRecipient("Alice", "alice@example.com")).build();
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.INBOX, null))
                .thenReturn(new FolderChanges(List.of(message), "next-page", null));
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.INBOX, "next-page")).thenReturn(last("cursor-final"));
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.SENT, null)).thenReturn(last("sent-cursor"));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "m1")).thenReturn(Optional.empty());

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(status.getMessagesSynced()).isEqualTo(1);
        assertThat(account.getSyncCursor()).isEqualTo("cursor-final");
        var saved = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFromAddress()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getFromName()).isEqualTo("Alice");
        assertThat(saved.getValue().getSubject()).isEqualTo("Hello");
    }

    @Test
    void syncAccount_UpdatesAnExistingMessageAndKeepsWhatTheProviderDidNotSend() {
        var existing = EmailMessage.builder().providerMessageId("m1").subject("Kept").receivedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .processingState(ProcessingState.DONE).build();
        when(mailboxClient.fetchChanges(eq("valid-token"), eq(SyncFolder.INBOX), any()))
                .thenReturn(last("c", SyncedMessage.builder().providerMessageId("m1").read(true).build()));
        when(mailboxClient.fetchChanges(eq("valid-token"), eq(SyncFolder.SENT), any())).thenReturn(last("s"));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "m1")).thenReturn(Optional.of(existing));

        mailSyncService.syncAccount(account);

        assertThat(existing.getSubject()).isEqualTo("Kept");
        assertThat(existing.isRead()).isTrue();
        assertThat(existing.getProcessingState()).isEqualTo(ProcessingState.DONE);
        assertThat(existing.getReceivedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void syncAccount_FetchesProfilePhotoOnce() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        when(mailboxClient.fetchProfilePhoto("valid-token")).thenReturn(Optional.of(new ImageData(jpeg, "image/jpeg")));
        emptyFolders();

        mailSyncService.syncAccount(account);

        assertThat(account.getPhoto()).isEqualTo(jpeg);
        assertThat(account.getPhotoContentType()).isEqualTo("image/jpeg");
        assertThat(account.getPhotoCheckedAt()).isNotNull();
    }

    @Test
    void syncAccount_RemembersAccountsWithoutPhoto() {
        when(mailboxClient.fetchProfilePhoto("valid-token")).thenReturn(Optional.empty());
        emptyFolders();

        mailSyncService.syncAccount(account);

        assertThat(account.getPhoto()).isNull();
        assertThat(account.getPhotoCheckedAt()).isNotNull();
    }

    @Test
    void syncAccount_SkipsPhotoWhenAlreadyChecked() {
        account.setPhotoCheckedAt(Instant.now());
        emptyFolders();

        mailSyncService.syncAccount(account);

        verify(mailboxClient, never()).fetchProfilePhoto(any());
    }

    @Test
    void syncAccount_ReplacesContactPhotosOncePerDay() {
        when(mailboxClient.fetchContactPhotos("valid-token")).thenReturn(List.of(
                new ContactPhotoData("bob@example.com", new ImageData(new byte[] {1}, "image/jpeg"))));
        emptyFolders();

        mailSyncService.syncAccount(account);

        verify(contactPhotoRepository).deleteByAccount_Id(account.getId());
        verify(contactPhotoRepository).saveAllAndFlush(argThat(photos -> {
            var list = (List<ContactPhoto>) photos;
            return list.size() == 1 && list.get(0).getEmailAddress().equals("bob@example.com") && list.get(0).getAccount() == account;
        }));
        assertThat(account.getContactsCheckedAt()).isNotNull();
    }

    @Test
    void syncAccount_SkipsContactPhotosCheckedToday() {
        account.setContactsCheckedAt(Instant.now().minusSeconds(3600));
        emptyFolders();

        mailSyncService.syncAccount(account);

        verify(mailboxClient, never()).fetchContactPhotos(any());
        verifyNoInteractions(contactPhotoRepository);
    }

    @Test
    void syncAccount_KeepsSyncingWhenContactsAreNotPermitted() {
        when(mailboxClient.fetchContactPhotos("valid-token")).thenThrow(new IllegalStateException("403 Forbidden"));
        emptyFolders();

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(account.getContactsCheckedAt()).isNotNull();
        verify(contactPhotoRepository, never()).deleteByAccount_Id(any());
    }

    private static SyncedMessage sentMessage(String id, Instant sentAt) {
        return SyncedMessage.builder()
                .providerMessageId(id)
                .subject("Re: Interview")
                .receivedAt(sentAt)
                .conversationId("conv-1")
                .to(List.of(new EmailRecipient("Anna", "anna@acme.com")))
                .bodyText("Thanks, Friday works.")
                .read(false)
                .build();
    }

    @Test
    void syncAccount_StoresSentItemsAsSentFolderAndKeepsThemOutOfTheAiQueue() {
        emptyInbox();
        var sentAt = Instant.parse("2026-10-07T09:00:00Z");
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.SENT, null)).thenReturn(last("sent-cursor-1", sentMessage("s1", sentAt)));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "s1")).thenReturn(Optional.empty());

        mailSyncService.syncAccount(account);

        var saved = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFolder()).isEqualTo("sent");
        assertThat(saved.getValue().getProcessingState()).isEqualTo(ProcessingState.SKIPPED);
        assertThat(saved.getValue().getReceivedAt()).isEqualTo(sentAt);
        assertThat(saved.getValue().isRead()).isTrue();
        assertThat(saved.getValue().getConversationId()).isEqualTo("conv-1");
        assertThat(saved.getValue().getBodyText()).isEqualTo("Thanks, Friday works.");
        assertThat(saved.getValue().getToRecipients()).extracting(EmailRecipient::address).containsExactly("anna@acme.com");
        assertThat(account.getSentSyncCursor()).isEqualTo("sent-cursor-1");
        assertThat(account.getSyncCursor()).isEqualTo("inbox-cursor");
    }

    @Test
    void syncAccount_InboxMessagesStayInTheInboxFolderAndAreQueuedForTheAi() {
        when(mailboxClient.fetchChanges(eq("valid-token"), eq(SyncFolder.INBOX), any()))
                .thenReturn(last("c", SyncedMessage.builder().providerMessageId("m1").receivedAt(Instant.now()).build()));
        when(mailboxClient.fetchChanges(eq("valid-token"), eq(SyncFolder.SENT), any())).thenReturn(last("s"));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "m1")).thenReturn(Optional.empty());

        mailSyncService.syncAccount(account);

        var saved = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFolder()).isEqualTo("inbox");
        assertThat(saved.getValue().getProcessingState()).isEqualTo(ProcessingState.NEW);
    }

    @Test
    void syncAccount_RetiresReplyDraftsOvertakenByTheSyncedMail() {
        emptyFolders();

        mailSyncService.syncAccount(account);

        verify(replyLifecycleService).retireOvertaken(account.getId());
    }

    @Test
    void syncAccount_SentItemsDoNotCountTowardsTheSyncedMessages() {
        emptyInbox();
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.SENT, null))
                .thenReturn(last("sent-cursor", sentMessage("s1", Instant.parse("2026-10-07T09:00:00Z"))));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(any(), any())).thenReturn(Optional.empty());

        assertThat(mailSyncService.syncAccount(account).getMessagesSynced()).isZero();
    }

    @Test
    void syncAccount_AFailingSentSyncDoesNotFailTheInboxSync() {
        emptyInbox();
        when(mailboxClient.fetchChanges(eq("valid-token"), eq(SyncFolder.SENT), any())).thenThrow(new IllegalStateException("403 Forbidden"));

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(status.getLastError()).isNull();
        assertThat(account.getSyncCursor()).isEqualTo("inbox-cursor");
        assertThat(account.getSentSyncCursor()).isNull();
    }

    @Test
    void syncAccount_ExpiredSentCursorIsResetAndSyncedFromScratch() {
        emptyInbox();
        account.setSentSyncCursor("stale");
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.SENT, "stale")).thenThrow(new SyncCursorExpiredException("410 Gone"));
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.SENT, null)).thenReturn(last("fresh"));

        mailSyncService.syncAccount(account);

        assertThat(account.getSentSyncCursor()).isEqualTo("fresh");
    }

    @Test
    void syncAccount_ExpiredInboxCursorIsResetAndSyncedFromScratch() {
        account.setProvider(MailProvider.GMAIL);
        account.setSyncCursor("123");
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.INBOX, "123")).thenThrow(new SyncCursorExpiredException("too old"));
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.INBOX, null)).thenReturn(last("900"));
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.SENT, null)).thenReturn(last("900"));

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(account.getSyncCursor()).isEqualTo("900");
        verify(mailProviderRegistry, atLeastOnce()).mailbox(MailProvider.GMAIL);
    }

    @Test
    void syncAccount_LastPageWithoutNewCursorKeepsTheStoredOne() {
        account.setSyncCursor("kept");
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.INBOX, "kept")).thenReturn(last(null));
        when(mailboxClient.fetchChanges("valid-token", SyncFolder.SENT, null)).thenReturn(last("s"));

        mailSyncService.syncAccount(account);

        assertThat(account.getSyncCursor()).isEqualTo("kept");
    }

    @Test
    void syncAccount_DemoAccountNeverCallsAProvider() {
        account.setProvider(MailProvider.DEMO);

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(status.getMessagesSynced()).isZero();
        verifyNoInteractions(mailProviderRegistry, oauthClient, mailboxClient, emailMessageRepository);
    }

    @Test
    void syncAllActiveAccounts_SkipsDemoAccounts() {
        account.setProvider(MailProvider.DEMO);
        when(mailAccountRepository.findByStatus(MailAccountStatus.ACTIVE)).thenReturn(List.of(account));

        mailSyncService.syncAllActiveAccounts();

        verifyNoInteractions(mailProviderRegistry, oauthClient, mailboxClient, emailMessageRepository);
    }
}
