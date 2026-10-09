package com.example.contor.service;

import com.example.contor.api.model.MailAccountState;
import com.example.contor.domain.*;
import com.example.contor.persistence.ContactPhotoRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
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
    private MsGraphAuthService msGraphAuthService;

    @Mock
    private MsGraphMailService msGraphMailService;

    @Mock
    private ContactPhotoRepository contactPhotoRepository;

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
    }

    @Test
    void syncAccount_RefreshesTokenWhenExpired() {
        account.setTokenExpiresAt(Instant.now().minusSeconds(10));
        when(msGraphAuthService.refreshToken("valid-refresh"))
                .thenReturn(new MsGraphAuthService.TokenResult("new-access", "new-refresh", Instant.now().plusSeconds(3600)));
        when(msGraphMailService.fetchMessagesDelta(eq("new-access"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "delta-link-1"));

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(account.getAccessToken()).isEqualTo("new-access");
        assertThat(account.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(account.getDeltaLink()).isEqualTo("delta-link-1");
        verify(mailAccountRepository, atLeastOnce()).saveAndFlush(account);
    }

    @Test
    void syncAccount_MarksNeedsReauthOnRefreshFailure() {
        account.setTokenExpiresAt(Instant.now().minusSeconds(10));
        when(msGraphAuthService.refreshToken("valid-refresh"))
                .thenThrow(new IllegalStateException("Invalid refresh token"));

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.NEEDS_REAUTH);
        assertThat(account.getStatus()).isEqualTo(MailAccountStatus.NEEDS_REAUTH);
        verify(mailAccountRepository).saveAndFlush(account);
        verifyNoInteractions(msGraphMailService);
    }

    @Test
    void syncAccount_PaginatesAndSavesMessages() {
        var msg1 = new Message();
        msg1.setId("m1");
        msg1.setSubject("Hello");
        msg1.setReceivedDateTime(OffsetDateTime.now());
        var sender = new Recipient();
        var emailAddr = new EmailAddress();
        emailAddr.setAddress("alice@example.com");
        emailAddr.setName("Alice");
        sender.setEmailAddress(emailAddr);
        msg1.setFrom(sender);

        when(msGraphMailService.fetchMessagesDelta("valid-token", null))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(msg1), "next-page-url", null));
        when(msGraphMailService.fetchMessagesDelta("valid-token", "next-page-url"))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "delta-link-final"));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "m1"))
                .thenReturn(Optional.empty());

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(status.getMessagesSynced()).isEqualTo(1);
        assertThat(account.getDeltaLink()).isEqualTo("delta-link-final");
        verify(emailMessageRepository).saveAndFlush(any(EmailMessage.class));
    }

    @Test
    void syncAccount_FetchesProfilePhotoOnce() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        when(msGraphMailService.fetchProfilePhoto("valid-token"))
                .thenReturn(Optional.of(new ImageData(jpeg, "image/jpeg")));
        when(msGraphMailService.fetchMessagesDelta(eq("valid-token"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "delta"));

        mailSyncService.syncAccount(account);

        assertThat(account.getPhoto()).isEqualTo(jpeg);
        assertThat(account.getPhotoContentType()).isEqualTo("image/jpeg");
        assertThat(account.getPhotoCheckedAt()).isNotNull();
    }

    @Test
    void syncAccount_RemembersAccountsWithoutPhoto() {
        when(msGraphMailService.fetchProfilePhoto("valid-token")).thenReturn(Optional.empty());
        when(msGraphMailService.fetchMessagesDelta(eq("valid-token"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "delta"));

        mailSyncService.syncAccount(account);

        assertThat(account.getPhoto()).isNull();
        assertThat(account.getPhotoCheckedAt()).isNotNull();
    }

    @Test
    void syncAccount_SkipsPhotoWhenAlreadyChecked() {
        account.setPhotoCheckedAt(Instant.now());
        when(msGraphMailService.fetchMessagesDelta(eq("valid-token"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "delta"));

        mailSyncService.syncAccount(account);

        verify(msGraphMailService, never()).fetchProfilePhoto(any());
    }

    @Test
    void syncAccount_ReplacesContactPhotosOncePerDay() {
        when(msGraphMailService.fetchContactPhotos("valid-token")).thenReturn(List.of(
                new MsGraphMailService.ContactPhotoData("bob@example.com", new ImageData(new byte[] {1}, "image/jpeg"))));
        when(msGraphMailService.fetchMessagesDelta(eq("valid-token"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "delta"));

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
        when(msGraphMailService.fetchMessagesDelta(eq("valid-token"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "delta"));

        mailSyncService.syncAccount(account);

        verify(msGraphMailService, never()).fetchContactPhotos(any());
        verifyNoInteractions(contactPhotoRepository);
    }

    @Test
    void syncAccount_KeepsSyncingWhenContactsAreNotPermitted() {
        when(msGraphMailService.fetchContactPhotos("valid-token")).thenThrow(new IllegalStateException("403 Forbidden"));
        when(msGraphMailService.fetchMessagesDelta(eq("valid-token"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "delta"));

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(account.getContactsCheckedAt()).isNotNull();
        verify(contactPhotoRepository, never()).deleteByAccount_Id(any());
    }

    private static Message sentMessage(String id, OffsetDateTime sentAt) {
        var message = new Message();
        message.setId(id);
        message.setSubject("Re: Interview");
        message.setSentDateTime(sentAt);
        message.setReceivedDateTime(sentAt.plusSeconds(5));
        message.setConversationId("conv-1");
        var recipient = new Recipient();
        var address = new EmailAddress();
        address.setAddress("anna@acme.com");
        address.setName("Anna");
        recipient.setEmailAddress(address);
        message.setToRecipients(List.of(recipient));
        var body = new com.microsoft.graph.models.ItemBody();
        body.setContentType(com.microsoft.graph.models.BodyType.Text);
        body.setContent("Thanks, Friday works.");
        message.setBody(body);
        return message;
    }

    private void emptyInbox() {
        when(msGraphMailService.fetchMessagesDelta(eq("valid-token"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "inbox-delta"));
    }

    @Test
    void syncAccount_StoresSentItemsAsSentFolderAndKeepsThemOutOfTheAiQueue() {
        emptyInbox();
        var sentAt = OffsetDateTime.parse("2026-10-07T09:00:00Z");
        when(msGraphMailService.fetchSentMessagesDelta("valid-token", null))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(sentMessage("s1", sentAt)), null, "sent-delta-1"));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "s1")).thenReturn(Optional.empty());

        mailSyncService.syncAccount(account);

        var saved = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFolder()).isEqualTo("sent");
        assertThat(saved.getValue().getProcessingState()).isEqualTo(ProcessingState.SKIPPED);
        assertThat(saved.getValue().getReceivedAt()).isEqualTo(sentAt.toInstant());
        assertThat(saved.getValue().isRead()).isTrue();
        assertThat(saved.getValue().getConversationId()).isEqualTo("conv-1");
        assertThat(saved.getValue().getToRecipients()).extracting(EmailRecipient::address).containsExactly("anna@acme.com");
        assertThat(account.getSentDeltaLink()).isEqualTo("sent-delta-1");
        assertThat(account.getDeltaLink()).isEqualTo("inbox-delta");
    }

    @Test
    void syncAccount_InboxMessagesStayInTheInboxFolderAndAreQueuedForTheAi() {
        var message = new Message();
        message.setId("m1");
        message.setReceivedDateTime(OffsetDateTime.now());
        when(msGraphMailService.fetchMessagesDelta(eq("valid-token"), any()))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(message), null, "delta"));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "m1")).thenReturn(Optional.empty());

        mailSyncService.syncAccount(account);

        var saved = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFolder()).isEqualTo("inbox");
        assertThat(saved.getValue().getProcessingState()).isEqualTo(ProcessingState.NEW);
    }

    @Test
    void syncAccount_SentItemsDoNotCountTowardsTheSyncedMessages() {
        emptyInbox();
        when(msGraphMailService.fetchSentMessagesDelta("valid-token", null)).thenReturn(new MsGraphMailService.DeltaResult(
                List.of(sentMessage("s1", OffsetDateTime.parse("2026-10-07T09:00:00Z"))), null, "sent-delta"));
        when(emailMessageRepository.findByAccount_IdAndProviderMessageId(any(), any())).thenReturn(Optional.empty());

        assertThat(mailSyncService.syncAccount(account).getMessagesSynced()).isZero();
    }

    @Test
    void syncAccount_AFailingSentSyncDoesNotFailTheInboxSync() {
        emptyInbox();
        when(msGraphMailService.fetchSentMessagesDelta(eq("valid-token"), any())).thenThrow(new IllegalStateException("403 Forbidden"));

        var status = mailSyncService.syncAccount(account);

        assertThat(status.getStatus()).isEqualTo(MailAccountState.ACTIVE);
        assertThat(status.getLastError()).isNull();
        assertThat(account.getDeltaLink()).isEqualTo("inbox-delta");
        assertThat(account.getSentDeltaLink()).isNull();
    }

    @Test
    void syncAccount_ExpiredSentDeltaLinkIsResetAndSyncedFromScratch() {
        emptyInbox();
        account.setSentDeltaLink("stale");
        when(msGraphMailService.fetchSentMessagesDelta("valid-token", "stale")).thenThrow(new IllegalStateException("410 Gone"));
        when(msGraphMailService.fetchSentMessagesDelta("valid-token", null))
                .thenReturn(new MsGraphMailService.DeltaResult(List.of(), null, "fresh"));

        mailSyncService.syncAccount(account);

        assertThat(account.getSentDeltaLink()).isEqualTo("fresh");
    }
}
