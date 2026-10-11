package com.example.contor.service;

import com.example.contor.config.WebProperties;
import com.example.contor.api.model.*;
import com.example.contor.domain.*;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.exception.MailAccountNotFoundException;
import com.example.contor.exception.MailAccountPhotoNotFoundException;
import com.example.contor.exception.MailCallbackException;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final int SUGGESTED_PEOPLE = 5;
    private static final int SUGGESTED_COMPANIES = 3;
    /** Senders read to build suggestions: enough to find the companies behind the top people. */
    private static final int SUGGESTION_SAMPLE = 50;
    /** Mail domains that do not stand for a company. */
    private static final Set<String> FREE_MAIL = Set.of("gmail.com", "googlemail.com", "outlook.com", "hotmail.com",
            "live.com", "msn.com", "icloud.com", "me.com", "yahoo.com", "gmx.at", "gmx.de", "gmx.net", "web.de", "aon.at",
            "chello.at", "a1.net", "proton.me", "protonmail.com");

    private final MailAccountRepository mailAccountRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final EmailLabelRepository emailLabelRepository;
    private final LabelRepository labelRepository;
    private final AppUserRepository appUserRepository;
    private final MsGraphAuthService msGraphAuthService;
    private final MsGraphMailService msGraphMailService;
    private final MailSyncService mailSyncService;
    private final WebProperties webProperties;
    private final RecordService recordService;
    private final CollectionSchemaService collectionSchemaService;
    private final RecordSourceRepository recordSourceRepository;

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

    /**
     * Completes the OAuth flow and returns the web app URL the browser continues to. Every failure becomes a
     * {@link MailCallbackException} with a web app URL carrying a user-facing {@code mailError}, so the browser
     * always ends up in the frontend; internal details only go to the log.
     */
    @Transactional
    public String handleCallback(String state, String code, String error, String errorDescription) {
        if (error != null && !error.isBlank()) {
            throw callbackFailed("access_denied".equals(error) ? "Microsoft sign-in was cancelled." : "Microsoft sign-in failed.", null);
        }
        if (state == null || state.isBlank() || code == null || code.isBlank()) {
            throw callbackFailed("The Microsoft sign-in response was incomplete. Please try again.", null);
        }
        try {
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
            account.setPhotoCheckedAt(null); // the sync below fetches the (possibly changed) photo again
            account.setStatus(MailAccountStatus.ACTIVE);
            mailAccountRepository.saveAndFlush(account);

            // Run initial synchronization
            mailSyncService.syncAccount(account);
        } catch (RuntimeException e) {
            log.warn("Mail account callback failed: {}", e.getMessage());
            throw callbackFailed("Connecting the mailbox failed. Please try again.", e);
        }
        return webProperties.url() + "/settings/accounts?connected=true";
    }

    private MailCallbackException callbackFailed(String message, Throwable cause) {
        return new MailCallbackException(
                webProperties.url() + "/settings/accounts?mailError=" + URLEncoder.encode(message, StandardCharsets.UTF_8), cause);
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
        account.setSentDeltaLink(null);
        mailAccountRepository.saveAndFlush(account);
    }

    @Transactional
    public ImageData getPhoto(Long accountId) {
        MailAccount account = mailAccountRepository.findById(new MailAccount.MailAccountId(accountId))
                .filter(candidate -> candidate.getOwner().getId().equals(currentUser()))
                .orElseThrow(() -> new MailAccountNotFoundException(accountId));
        if (account.getPhoto() == null) {
            throw new MailAccountPhotoNotFoundException(accountId);
        }
        return new ImageData(account.getPhoto(), account.getPhotoContentType());
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
                                   EmailSearchField field, Boolean answered, Integer page, Integer size) {
        var criteria = new EmailSpecifications.Criteria(
                currentUser(),
                accountId != null ? new MailAccount.MailAccountId(accountId) : null,
                labelId != null ? new Label.LabelId(labelId) : null,
                processingState != null ? ProcessingState.valueOf(processingState.name()) : null,
                q,
                field != null ? SearchField.valueOf(field.name()) : SearchField.ALL,
                answered);
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<EmailMessage> result = emailMessageRepository.findAll(EmailSpecifications.matching(criteria), pageable);

        return toPage(result);
    }

    /**
     * Search-box suggestions: the most frequent matching senders (people), then the companies (sender domains) whose
     * domain matches. Their {@code value} is what to search with field PEOPLE.
     */
    @Transactional
    public List<EmailSuggestionDto> suggestEmailSenders(String q) {
        String text = q.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return List.of();
        }
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<SenderCount> senders = emailMessageRepository.findSenders(currentUser(), pattern, PageRequest.of(0, SUGGESTION_SAMPLE));

        List<EmailSuggestionDto> suggestions = new ArrayList<>(senders.stream()
                .limit(SUGGESTED_PEOPLE)
                .map(sender -> new EmailSuggestionDto(EmailSuggestionKind.PERSON,
                        sender.name() != null && !sender.name().isBlank() ? sender.name() : sender.address(),
                        sender.address(), sender.emails()))
                .toList());
        Map<String, Long> companies = new LinkedHashMap<>();
        for (SenderCount sender : senders) {
            String domain = sender.address().substring(sender.address().indexOf('@') + 1);
            if (domain.contains(text) && !FREE_MAIL.contains(domain)) {
                companies.merge(domain, sender.emails(), Long::sum);
            }
        }
        companies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(SUGGESTED_COMPANIES)
                .forEach(company -> suggestions.add(new EmailSuggestionDto(EmailSuggestionKind.COMPANY,
                        company.getKey(), "@" + company.getKey(), company.getValue())));
        return suggestions;
    }

    /** Emails of the current user's mailboxes in which a record comes up (see {@link EmailSpecifications#involving}). */
    @Transactional
    public EmailPageDto listRecordEmails(Long collectionId, Long recordId, Integer page, Integer size) {
        RecordDto record = recordService.getRecord(collectionId, recordId);
        RecordContacts contacts = RecordContacts.of(collectionSchemaService.findById(collectionId), record);
        List<Long> sourceEmailIds = recordSourceRepository.findByCollection_IdAndRecordId(new Collection.CollectionId(collectionId), recordId)
                .stream()
                .map(source -> source.getEmail().getId().id())
                .distinct()
                .toList();
        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        if (contacts.isEmpty() && sourceEmailIds.isEmpty()) {
            return new EmailPageDto(new ArrayList<>(), pageNumber, pageSize, 0L, 0);
        }

        var owner = new EmailSpecifications.Criteria(currentUser(), null, null, null, null);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<EmailMessage> result = emailMessageRepository.findAll(EmailSpecifications.matching(owner)
                .and(EmailSpecifications.involving(sourceEmailIds, contacts.addresses(), contacts.domains())), pageable);

        return toPage(result);
    }

    /** One page of summaries with labels and thread info, each from a single query for the whole page. */
    private EmailPageDto toPage(Page<EmailMessage> result) {
        List<EmailMessage> emails = result.getContent();
        Map<Long, List<EmailLabelDto>> labels = labelsOf(emails.stream().map(email -> email.getId().id()).toList());
        Map<String, ThreadStat> threads = threadStatsOf(emails);
        return new EmailPageDto(
                new ArrayList<>(emails.stream().map(email -> {
                    ThreadStat thread = email.getConversationId() != null
                            ? threads.get(threadKey(email.getAccount().getId().id(), email.getConversationId())) : null;
                    boolean answered = thread != null && thread.lastSentAt() != null && thread.lastSentAt().isAfter(email.getReceivedAt());
                    return MailMapper.toSummaryDto(email, labels.getOrDefault(email.getId().id(), List.of()),
                            thread != null ? thread.messages().intValue() : 1, answered);
                }).toList()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private Map<String, ThreadStat> threadStatsOf(List<EmailMessage> emails) {
        List<String> conversationIds = emails.stream().map(EmailMessage::getConversationId).filter(Objects::nonNull).distinct().toList();
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        return emailMessageRepository.threadStats(currentUser(), conversationIds).stream()
                .collect(Collectors.toMap(stat -> threadKey(stat.accountId(), stat.conversationId()), Function.identity(), (a, b) -> a));
    }

    private static String threadKey(Long accountId, String conversationId) {
        return accountId + "/" + conversationId;
    }

    /** The conversation of an email in its mailbox, oldest first, with the user's own replies (Sent Items). */
    @Transactional
    public EmailThreadDto getEmailThread(Long id) {
        EmailMessage email = emailMessageRepository.findById(new EmailMessage.EmailMessageId(id))
                .filter(candidate -> candidate.getAccount().getOwner().getId().equals(currentUser()))
                .orElseThrow(() -> new EmailNotFoundException(id));
        List<EmailMessage> messages = email.getConversationId() == null
                ? List.of(email)
                : emailMessageRepository.findThread(email.getAccount().getId(), email.getConversationId());
        Map<Long, List<EmailLabelDto>> labels = labelsOf(messages.stream().map(message -> message.getId().id()).toList());
        return new EmailThreadDto(new ArrayList<>(messages.stream()
                .map(message -> MailMapper.toDto(message, labels.getOrDefault(message.getId().id(), List.of())))
                .toList()))
                .conversationId(email.getConversationId());
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
