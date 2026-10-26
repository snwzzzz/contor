package com.example.contor.service;

import com.example.contor.api.model.MailAccountState;
import com.example.contor.api.model.MailSyncStatusDto;
import com.example.contor.domain.ContactPhoto;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailAccountStatus;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.SyncFolder;
import com.example.contor.exception.SyncCursorExpiredException;
import com.example.contor.mailprovider.ContactPhotoData;
import com.example.contor.mailprovider.FolderChanges;
import com.example.contor.mailprovider.MailProviderRegistry;
import com.example.contor.mailprovider.MailboxClient;
import com.example.contor.mailprovider.OAuthTokens;
import com.example.contor.mailprovider.SyncedMessage;
import com.example.contor.persistence.ContactPhotoRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider-neutral mail sync: refreshes the token, then pages through the inbox and the sent mail with the account's
 * {@link MailboxClient}, keeping one sync cursor per folder. Providers plug in through {@link MailProviderRegistry}.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MailSyncService {

    private final MailAccountRepository mailAccountRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final MailProviderRegistry mailProviderRegistry;
    private final ContactPhotoRepository contactPhotoRepository;
    private final ReplyLifecycleService replyLifecycleService;

    private static final Duration CONTACTS_REFRESH = Duration.ofDays(1);

    @Transactional
    public MailSyncStatusDto syncAccount(MailAccount account) {
        if (!account.getProvider().synced()) {
            // the demo mailbox is imported once and has no provider account behind it
            return new MailSyncStatusDto(account.getId().id(), MailAccountState.ACTIVE).messagesSynced(0)
                    .lastSyncedAt(account.getLastSyncedAt() != null ? account.getLastSyncedAt().atOffset(ZoneOffset.UTC) : null);
        }
        MailboxClient mailbox = mailProviderRegistry.mailbox(account.getProvider());
        String token;
        try {
            token = ensureValidToken(account);
        } catch (Exception e) {
            log.error("Token refresh failed for account {}: {}", account.getId().id(), e.getMessage());
            account.setStatus(MailAccountStatus.NEEDS_REAUTH);
            mailAccountRepository.saveAndFlush(account);
            return new MailSyncStatusDto(account.getId().id(), MailAccountState.NEEDS_REAUTH)
                    .lastError("Token refresh failed: " + e.getMessage());
        }

        if (account.getPhotoCheckedAt() == null) {
            refreshPhoto(account, mailbox, token);
        }
        if (account.getContactsCheckedAt() == null
                || account.getContactsCheckedAt().isBefore(Instant.now().minus(CONTACTS_REFRESH))) {
            refreshContactPhotos(account, mailbox, token);
        }

        try {
            return doSync(account, mailbox, token);
        } catch (Exception e) {
            log.error("Mail sync failed for account {}: {}", account.getId().id(), e.getMessage());
            return new MailSyncStatusDto(account.getId().id(), MailAccountState.valueOf(account.getStatus().name()))
                    .lastError("Sync failed: " + e.getMessage());
        }
    }

    /** Once per (re)connect: store the profile photo, or remember that there is none (saved even if the sync fails). */
    private void refreshPhoto(MailAccount account, MailboxClient mailbox, String token) {
        mailbox.fetchProfilePhoto(token).ifPresentOrElse(photo -> {
            account.setPhoto(photo.bytes());
            account.setPhotoContentType(photo.contentType());
        }, () -> {
            account.setPhoto(null);
            account.setPhotoContentType(null);
        });
        account.setPhotoCheckedAt(Instant.now());
        mailAccountRepository.saveAndFlush(account);
    }

    /**
     * Replaces the account's contact photos. Without the contacts permission (accounts connected before it was
     * requested) the provider refuses; that is logged and retried a day later, the mail sync goes on.
     */
    private void refreshContactPhotos(MailAccount account, MailboxClient mailbox, String token) {
        try {
            Map<String, ContactPhotoData> byAddress = new LinkedHashMap<>();
            mailbox.fetchContactPhotos(token).forEach(contact -> byAddress.putIfAbsent(contact.emailAddress(), contact));
            List<ContactPhoto> photos = byAddress.values().stream()
                    .map(contact -> ContactPhoto.builder()
                            .account(account)
                            .emailAddress(contact.emailAddress())
                            .image(contact.photo().bytes())
                            .contentType(contact.photo().contentType())
                            .build())
                    .toList();
            contactPhotoRepository.deleteByAccount_Id(account.getId());
            contactPhotoRepository.saveAllAndFlush(photos);
        } catch (RuntimeException e) {
            log.info("Contact photos unavailable for account {}: {}", account.getId().id(), e.getMessage());
        }
        account.setContactsCheckedAt(Instant.now());
        mailAccountRepository.saveAndFlush(account);
    }

    private MailSyncStatusDto doSync(MailAccount account, MailboxClient mailbox, String token) {
        FolderSync inbox = syncFolder(account, mailbox, token, SyncFolder.INBOX);
        account.setSyncCursor(inbox.cursor());
        try {
            account.setSentSyncCursor(syncFolder(account, mailbox, token, SyncFolder.SENT).cursor());
        } catch (RuntimeException e) {
            // Sent mail is only context: a failure here must not fail the inbox sync, it is retried next time.
            log.warn("Sent mail sync failed for account {}: {}", account.getId().id(), e.getMessage());
        }
        Instant now = Instant.now();
        account.setLastSyncedAt(now);
        account.setStatus(MailAccountStatus.ACTIVE);
        mailAccountRepository.saveAndFlush(account);
        // New mail may have answered or overtaken a pending reply draft (same transaction: it sees what was just synced).
        replyLifecycleService.retireOvertaken(account.getId());

        return new MailSyncStatusDto(account.getId().id(), MailAccountState.ACTIVE)
                .lastSyncedAt(now.atOffset(ZoneOffset.UTC))
                .messagesSynced(inbox.messages());
    }

    private record FolderSync(int messages, String cursor) {
    }

    /** Pages through one folder's changes; an expired cursor restarts that folder from scratch. */
    private FolderSync syncFolder(MailAccount account, MailboxClient mailbox, String token, SyncFolder folder) {
        String storedCursor = folder == SyncFolder.SENT ? account.getSentSyncCursor() : account.getSyncCursor();
        String currentCursor = storedCursor;
        int total = 0;

        while (true) {
            FolderChanges changes;
            try {
                changes = mailbox.fetchChanges(token, folder, currentCursor);
            } catch (SyncCursorExpiredException e) {
                if (currentCursor == null) {
                    throw e;
                }
                log.warn("Sync cursor of {} expired for account {}, syncing it from scratch", folder, account.getId().id());
                storedCursor = null;
                currentCursor = null;
                changes = mailbox.fetchChanges(token, folder, null);
            }

            for (SyncedMessage message : changes.messages()) {
                upsertMessage(account, message, folder);
                total++;
            }

            if (changes.hasNextPage()) {
                currentCursor = changes.nextPageCursor();
            } else {
                return new FolderSync(total, changes.newSyncCursor() != null ? changes.newSyncCursor() : storedCursor);
            }
        }
    }

    private void upsertMessage(MailAccount account, SyncedMessage message, SyncFolder folder) {
        if (message.providerMessageId() == null) {
            return;
        }
        boolean sent = folder == SyncFolder.SENT;
        Optional<EmailMessage> existing = emailMessageRepository.findByAccount_IdAndProviderMessageId(
                account.getId(), message.providerMessageId());

        EmailMessage email = existing.orElseGet(() -> EmailMessage.builder()
                .account(account)
                .providerMessageId(message.providerMessageId())
                .receivedAt(Instant.now())
                .processingState(sent ? ProcessingState.SKIPPED : ProcessingState.NEW)
                .build());

        if (message.internetMessageId() != null) {
            email.setInternetMessageId(message.internetMessageId());
        }
        if (message.conversationId() != null) {
            email.setConversationId(message.conversationId());
        }
        email.setFolder(folder.storedName());
        if (message.from() != null) {
            email.setFromAddress(message.from().address());
            email.setFromName(message.from().name());
        }
        email.setToRecipients(message.to() != null ? message.to() : List.of());
        email.setCcRecipients(message.cc() != null ? message.cc() : List.of());
        if (message.subject() != null) {
            email.setSubject(message.subject());
        }
        if (message.snippet() != null) {
            email.setSnippet(message.snippet());
        }
        if (message.bodyHtml() != null) {
            email.setBodyHtml(message.bodyHtml());
        } else if (message.bodyText() != null) {
            email.setBodyText(message.bodyText());
        }
        if (message.receivedAt() != null) {
            email.setReceivedAt(message.receivedAt());
        }
        if (sent) {
            email.setRead(true);
        } else if (message.read() != null) {
            email.setRead(message.read());
        }
        if (message.hasAttachments() != null) {
            email.setHasAttachments(message.hasAttachments());
        }
        emailMessageRepository.saveAndFlush(email);
    }

    private String ensureValidToken(MailAccount account) {
        Instant expiresAt = account.getTokenExpiresAt();
        if (expiresAt != null && expiresAt.isAfter(Instant.now().plusSeconds(120)) && account.getAccessToken() != null) {
            return account.getAccessToken();
        }

        if (account.getRefreshToken() == null) {
            throw new IllegalStateException("No refresh token available");
        }

        OAuthTokens refreshed = mailProviderRegistry.oauth(account.getProvider()).refresh(account.getRefreshToken());
        account.setAccessToken(refreshed.accessToken());
        if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
            account.setRefreshToken(refreshed.refreshToken());
        }
        account.setTokenExpiresAt(refreshed.expiresAt());
        mailAccountRepository.saveAndFlush(account);
        return refreshed.accessToken();
    }

    @Scheduled(fixedDelayString = "${app.mail.sync.interval:5m}")
    public void syncAllActiveAccounts() {
        List<MailAccount> accounts = mailAccountRepository.findByStatus(MailAccountStatus.ACTIVE);
        for (MailAccount account : accounts) {
            if (!account.getProvider().synced()) {
                continue;
            }
            try {
                syncAccount(account);
            } catch (Exception e) {
                log.error("Scheduled sync failed for account {}: {}", account.getId().id(), e.getMessage());
            }
        }
    }
}
