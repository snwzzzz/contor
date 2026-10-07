package com.example.contor.service;

import com.example.contor.api.model.*;
import com.example.contor.domain.*;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.exception.MailAccountNotFoundException;
import com.example.contor.mapper.MailMapper;
import com.example.contor.persistence.*;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mail service managing connected mailboxes, OAuth2 flow with Microsoft Graph,
 * incremental message synchronization, and email queries.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MailService {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final MailAccountRepository mailAccountRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final EmailLabelRepository emailLabelRepository;
    private final LabelRepository labelRepository;
    private final AppUserRepository appUserRepository;
    private final MsGraphAuthService msGraphAuthService;
    private final MsGraphMailService msGraphMailService;
    private final MailSyncService mailSyncService;

    @Transactional
    public List<MailAccountDto> listMailAccounts() {
        return mailAccountRepository.findByOwner_Id(currentUser()).stream()
                .map(MailMapper::toDto)
                .toList();
    }

    @Transactional
    public MailAuthorizeUrlDto connectAccount(ConnectMailAccountCommand command) {
        String emailHint = command != null ? command.getEmailHint() : null;
        String url = msGraphAuthService.buildAuthorizeUrl(currentUser().id(), emailHint);
        return new MailAuthorizeUrlDto(url);
    }

    @Transactional
    public void handleCallback(String state, String code, String error, String errorDescription) {
        if (error != null && !error.isBlank()) {
            throw new IllegalArgumentException("OAuth authorization failed: " +
                    (errorDescription != null ? errorDescription : error));
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Missing OAuth authorization code");
        }

        Long userId = msGraphAuthService.validateStateAndExtractUserId(state);
        MsGraphAuthService.TokenResult tokens = msGraphAuthService.exchangeCode(code);
        MsGraphMailService.UserProfile profile = msGraphMailService.fetchUserProfile(tokens.accessToken());

        AppUser.AppUserId ownerId = new AppUser.AppUserId(userId);
        AppUser owner = appUserRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalStateException("User not found with id: " + userId));

        Email email = new Email(profile.emailAddress());
        MailAccount account = mailAccountRepository.findByOwner_IdAndEmailAddress(ownerId, email)
                .orElseGet(() -> MailAccount.builder()
                        .owner(owner)
                        .provider(MailProvider.MICROSOFT_GRAPH)
                        .emailAddress(email)
                        .build());

        account.setDisplayName(profile.displayName());
        account.setAccessToken(tokens.accessToken());
        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            account.setRefreshToken(tokens.refreshToken());
        }
        account.setTokenExpiresAt(tokens.expiresAt());
        account.setStatus(MailAccountStatus.ACTIVE);
        mailAccountRepository.saveAndFlush(account);

        // Run initial synchronization
        mailSyncService.syncAccount(account);
    }

    @Transactional
    public MailSyncStatusDto syncAccount(Long accountId) {
        MailAccount account = mailAccountRepository.findById(new MailAccount.MailAccountId(accountId))
                .filter(candidate -> candidate.getOwner().getId().equals(currentUser()))
                .orElseThrow(() -> new MailAccountNotFoundException(accountId));

        return mailSyncService.syncAccount(account);
    }

    @Transactional
    public void disconnectAccount(Long accountId) {
        MailAccount account = mailAccountRepository.findById(new MailAccount.MailAccountId(accountId))
                .filter(candidate -> candidate.getOwner().getId().equals(currentUser()))
                .orElseThrow(() -> new MailAccountNotFoundException(accountId));

        account.setStatus(MailAccountStatus.DISABLED);
        account.setAccessToken(null);
        account.setRefreshToken(null);
        account.setTokenExpiresAt(null);
        account.setDeltaLink(null);
        mailAccountRepository.saveAndFlush(account);
    }

    @Transactional
    public MailSyncStatusDto getSyncStatus(Long accountId) {
        MailAccount account = mailAccountRepository.findById(new MailAccount.MailAccountId(accountId))
                .filter(candidate -> candidate.getOwner().getId().equals(currentUser()))
                .orElseThrow(() -> new MailAccountNotFoundException(accountId));

        return new MailSyncStatusDto(accountId, MailAccountState.valueOf(account.getStatus().name()))
                .lastSyncedAt(account.getLastSyncedAt() != null ? account.getLastSyncedAt().atOffset(ZoneOffset.UTC) : null);
    }

    @Transactional
    public EmailPageDto listEmails(Long accountId, Long labelId, EmailProcessingState processingState, String q,
                                   Integer page, Integer size) {
        var criteria = new EmailSpecifications.Criteria(
                currentUser(),
                accountId != null ? new MailAccount.MailAccountId(accountId) : null,
                labelId != null ? new Label.LabelId(labelId) : null,
                processingState != null ? ProcessingState.valueOf(processingState.name()) : null,
                q);
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<EmailMessage> result = emailMessageRepository.findAll(EmailSpecifications.matching(criteria), pageable);

        Map<Long, List<EmailLabelDto>> labels = labelsOf(result.getContent().stream().map(email -> email.getId().id()).toList());
        return new EmailPageDto(
                new ArrayList<>(result.getContent().stream()
                        .map(email -> MailMapper.toSummaryDto(email, labels.getOrDefault(email.getId().id(), List.of())))
                        .toList()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public EmailDto getEmail(Long id) {
        EmailMessage email = emailMessageRepository.findById(new EmailMessage.EmailMessageId(id))
                .filter(candidate -> candidate.getAccount().getOwner().getId().equals(currentUser()))
                .orElseThrow(() -> new EmailNotFoundException(id));
        return MailMapper.toDto(email, labelsOf(List.of(id)).getOrDefault(id, List.of()));
    }

    /** Labels of several emails with two queries (no N+1). */
    private Map<Long, List<EmailLabelDto>> labelsOf(List<Long> emailIds) {
        if (emailIds.isEmpty()) {
            return Map.of();
        }
        List<EmailLabel> emailLabels = emailLabelRepository.findById_EmailIdIn(emailIds);
        if (emailLabels.isEmpty()) {
            return Map.of();
        }
        List<Label.LabelId> labelIds = emailLabels.stream().map(el -> new Label.LabelId(el.getId().labelId())).distinct().toList();
        Map<Long, Label> labelsById = labelRepository.findAllById(labelIds).stream()
                .collect(Collectors.toMap(label -> label.getId().id(), Function.identity()));
        Map<Long, List<EmailLabelDto>> byEmail = new HashMap<>();
        for (EmailLabel emailLabel : emailLabels) {
            Label label = labelsById.get(emailLabel.getId().labelId());
            if (label != null) {
                byEmail.computeIfAbsent(emailLabel.getId().emailId(), key -> new ArrayList<>()).add(MailMapper.toDto(emailLabel, label));
            }
        }
        return byEmail;
    }

    private static AppUser.AppUserId currentUser() {
        return new AppUser.AppUserId(CurrentActor.userId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user")));
    }
}
