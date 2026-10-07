package com.example.contor.service;

import com.example.contor.api.model.MailAccountState;
import com.example.contor.api.model.MailSyncStatusDto;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailAccountStatus;
import com.example.contor.domain.ProcessingState;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.Message;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class MailSyncService {

    private final MailAccountRepository mailAccountRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final MsGraphAuthService msGraphAuthService;
    private final MsGraphMailService msGraphMailService;

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

        try {
            return doDeltaSync(account, token);
        } catch (Exception e) {
            log.error("Delta sync failed for account {}: {}", account.getId().id(), e.getMessage());
            return new MailSyncStatusDto(account.getId().id(), MailAccountState.valueOf(account.getStatus().name()))
                    .lastError("Sync failed: " + e.getMessage());
        }
    }

    private MailSyncStatusDto doDeltaSync(MailAccount account, String token) {
        String currentLink = account.getDeltaLink();
        int totalSynced = 0;
        String nextDeltaLink = null;

        while (true) {
            MsGraphMailService.DeltaResult result;
            try {
                result = msGraphMailService.fetchMessagesDelta(token, currentLink);
            } catch (Exception e) {
                if (currentLink != null && e.getMessage() != null && e.getMessage().contains("410")) {
                    log.warn("Delta token expired for account {}, resetting sync link", account.getId().id());
                    account.setDeltaLink(null);
                    currentLink = null;
                    result = msGraphMailService.fetchMessagesDelta(token, null);
                } else {
                    throw e;
                }
            }

            for (Message message : result.messages()) {
                upsertMessage(account, message);
                totalSynced++;
            }

            if (result.nextLink() != null && !result.nextLink().isBlank()) {
                currentLink = result.nextLink();
            } else {
                nextDeltaLink = result.newDeltaLink();
                break;
            }
        }

        if (nextDeltaLink != null) {
            account.setDeltaLink(nextDeltaLink);
        }
        Instant now = Instant.now();
        account.setLastSyncedAt(now);
        account.setStatus(MailAccountStatus.ACTIVE);
        mailAccountRepository.saveAndFlush(account);

        return new MailSyncStatusDto(account.getId().id(), MailAccountState.ACTIVE)
                .lastSyncedAt(now.atOffset(ZoneOffset.UTC))
                .messagesSynced(totalSynced);
    }

    private void upsertMessage(MailAccount account, Message message) {
        if (message.getId() == null) {
            return;
        }
        Optional<EmailMessage> existing = emailMessageRepository.findByAccount_IdAndProviderMessageId(
                account.getId(), message.getId());

        EmailMessage email = existing.orElseGet(() -> EmailMessage.builder()
                .account(account)
                .providerMessageId(message.getId())
                .receivedAt(Instant.now())
                .processingState(ProcessingState.NEW)
                .build());

        if (message.getInternetMessageId() != null) {
            email.setInternetMessageId(message.getInternetMessageId());
        }
        if (message.getConversationId() != null) {
            email.setConversationId(message.getConversationId());
        }
        email.setFolder("inbox");
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
        if (message.getReceivedDateTime() != null) {
            email.setReceivedAt(message.getReceivedDateTime().toInstant());
        }
        if (message.getIsRead() != null) {
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
