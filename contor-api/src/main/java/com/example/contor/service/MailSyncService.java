package com.example.contor.service;

import com.example.contor.api.model.MailAccountState;
import com.example.contor.api.model.MailSyncStatusDto;
import com.example.contor.domain.ContactPhoto;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailAccountStatus;
import com.example.contor.domain.ProcessingState;
import com.example.contor.persistence.ContactPhotoRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.Message;
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

@Slf4j
@RequiredArgsConstructor
@Service
public class MailSyncService {

    private final MailAccountRepository mailAccountRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final MsGraphAuthService msGraphAuthService;
    private final MsGraphMailService msGraphMailService;
    private final ContactPhotoRepository contactPhotoRepository;
    private final ReplyLifecycleService replyLifecycleService;

    private static final Duration CONTACTS_REFRESH = Duration.ofDays(1);

    @Transactional
    public MailSyncStatusDto syncAccount(MailAccount account) {
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
            refreshPhoto(account, token);
        }
        if (account.getContactsCheckedAt() == null
                || account.getContactsCheckedAt().isBefore(Instant.now().minus(CONTACTS_REFRESH))) {
            refreshContactPhotos(account, token);
        }

        try {
            return doDeltaSync(account, token);
        } catch (Exception e) {
            log.error("Delta sync failed for account {}: {}", account.getId().id(), e.getMessage());
            return new MailSyncStatusDto(account.getId().id(), MailAccountState.valueOf(account.getStatus().name()))
                    .lastError("Sync failed: " + e.getMessage());
        }
    }

    /** Once per (re)connect: store the profile photo, or remember that there is none (saved even if the sync fails). */
    private void refreshPhoto(MailAccount account, String token) {
        msGraphMailService.fetchProfilePhoto(token).ifPresentOrElse(photo -> {
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
     * Replaces the account's contact photos. Without the Contacts.Read permission (accounts connected before it was
     * requested) Graph refuses; that is logged and retried a day later, the mail sync goes on.
     */
    private void refreshContactPhotos(MailAccount account, String token) {
        try {
            Map<String, MsGraphMailService.ContactPhotoData> byAddress = new LinkedHashMap<>();
            msGraphMailService.fetchContactPhotos(token).forEach(contact -> byAddress.putIfAbsent(contact.emailAddress(), contact));
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

    private MailSyncStatusDto doDeltaSync(MailAccount account, String token) {
        FolderSync inbox = syncFolder(account, token, EmailMessage.FOLDER_INBOX);
        account.setDeltaLink(inbox.deltaLink());
        try {
            account.setSentDeltaLink(syncFolder(account, token, EmailMessage.FOLDER_SENT).deltaLink());
        } catch (RuntimeException e) {
            // Sent mail is only context: a failure here must not fail the inbox sync, it is retried next time.
            log.warn("Sent items sync failed for account {}: {}", account.getId().id(), e.getMessage());
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

    private record FolderSync(int messages, String deltaLink) {
    }

    /** Pages through one folder's delta; an expired delta link (410) restarts that folder from scratch. */
    private FolderSync syncFolder(MailAccount account, String token, String folder) {
        boolean sent = EmailMessage.FOLDER_SENT.equals(folder);
        String storedLink = sent ? account.getSentDeltaLink() : account.getDeltaLink();
        String currentLink = storedLink;
        int total = 0;

        while (true) {
            MsGraphMailService.DeltaResult result;
            try {
                result = fetch(token, sent, currentLink);
            } catch (Exception e) {
                if (currentLink != null && e.getMessage() != null && e.getMessage().contains("410")) {
                    log.warn("Delta token of {} expired for account {}, resetting sync link", folder, account.getId().id());
                    storedLink = null;
                    currentLink = null;
                    result = fetch(token, sent, null);
                } else {
                    throw e;
                }
            }

            for (Message message : result.messages()) {
                upsertMessage(account, message, folder);
                total++;
            }

            if (result.nextLink() != null && !result.nextLink().isBlank()) {
                currentLink = result.nextLink();
            } else {
                return new FolderSync(total, result.newDeltaLink() != null ? result.newDeltaLink() : storedLink);
            }
        }
    }

    private MsGraphMailService.DeltaResult fetch(String token, boolean sent, String link) {
        return sent ? msGraphMailService.fetchSentMessagesDelta(token, link) : msGraphMailService.fetchMessagesDelta(token, link);
    }

    private void upsertMessage(MailAccount account, Message message, String folder) {
        if (message.getId() == null) {
            return;
        }
        boolean sent = EmailMessage.FOLDER_SENT.equals(folder);
        Optional<EmailMessage> existing = emailMessageRepository.findByAccount_IdAndProviderMessageId(
                account.getId(), message.getId());

        EmailMessage email = existing.orElseGet(() -> EmailMessage.builder()
                .account(account)
                .providerMessageId(message.getId())
                .receivedAt(Instant.now())
                .processingState(sent ? ProcessingState.SKIPPED : ProcessingState.NEW)
                .build());

        if (message.getInternetMessageId() != null) {
            email.setInternetMessageId(message.getInternetMessageId());
        }
        if (message.getConversationId() != null) {
            email.setConversationId(message.getConversationId());
        }
        email.setFolder(folder);
        if (message.getFrom() != null && message.getFrom().getEmailAddress() != null) {
            email.setFromAddress(message.getFrom().getEmailAddress().getAddress());
            email.setFromName(message.getFrom().getEmailAddress().getName());
        }
        email.setToRecipients(MsGraphMailService.mapRecipients(message.getToRecipients()));
        email.setCcRecipients(MsGraphMailService.mapRecipients(message.getCcRecipients()));
        if (message.getSubject() != null) {
            email.setSubject(message.getSubject());
        }
        if (message.getBodyPreview() != null) {
            email.setSnippet(message.getBodyPreview());
        }
        if (message.getBody() != null) {
            if (message.getBody().getContentType() == BodyType.Html) {
                email.setBodyHtml(message.getBody().getContent());
            } else {
                email.setBodyText(message.getBody().getContent());
            }
        }
        if (sent && message.getSentDateTime() != null) {
            email.setReceivedAt(message.getSentDateTime().toInstant());
        } else if (message.getReceivedDateTime() != null) {
            email.setReceivedAt(message.getReceivedDateTime().toInstant());
        }
        if (sent) {
            email.setRead(true);
        } else if (message.getIsRead() != null) {
            email.setRead(message.getIsRead());
        }
        if (message.getHasAttachments() != null) {
            email.setHasAttachments(message.getHasAttachments());
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

        MsGraphAuthService.TokenResult refreshed = msGraphAuthService.refreshToken(account.getRefreshToken());
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
            try {
                syncAccount(account);
            } catch (Exception e) {
                log.error("Scheduled sync failed for account {}: {}", account.getId().id(), e.getMessage());
            }
        }
    }
}
