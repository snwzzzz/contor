package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.EmailTextCleaner;
import com.example.contor.ai.ImportanceSignals;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.ai.ReplyCandidateFilter;
import com.example.contor.ai.ReplyThread;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.ReplySuggestionState;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.exception.ReplySuggestionUnavailableException;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.security.CurrentActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * Suggests reply drafts as DRAFT_REPLY proposals (never sent). For new inbox mail ({@link #suggestIfNeeded}, called
 * by the AI worker when the user's {@code replies} permission is SUGGEST): cheap rules ({@link ReplyCandidateFilter}),
 * then a triage call, then the draft. On demand ({@link #requestSuggestion}): the draft right away, on the AI thread.
 * Model calls run outside transactions; a new draft replaces the conversation's pending ones. Never logs mail text.
 */
@Slf4j
@Service
public class ReplySuggestionService {

    private static final int BODY_CHARS = 4000;
    private static final int MAX_GROUNDING_RECORDS = 5;

    private final EmailMessageRepository emailMessageRepository;
    private final MailAccountRepository mailAccountRepository;
    private final ProposalRepository proposalRepository;
    private final ProposalService proposalService;
    private final ReplyLifecycleService replyLifecycleService;
    private final ReplyTriageService replyTriageService;
    private final ReplyComposerService replyComposerService;
    private final CollectionSchemaService collectionSchemaService;
    private final RecordService recordService;
    private final AiSettingsService aiSettingsService;
    private final ImportanceSignalService importanceSignalService;
    private final AiProperties properties;
    private final AiExecutors aiExecutors;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    /** On-demand suggestions that are queued/running or failed last time; absent = IDLE. One app instance only. */
    private final Map<Long, ReplySuggestionState> onDemand = new ConcurrentHashMap<>();

    public ReplySuggestionService(EmailMessageRepository emailMessageRepository, MailAccountRepository mailAccountRepository,
                                  ProposalRepository proposalRepository, ProposalService proposalService,
                                  ReplyLifecycleService replyLifecycleService, ReplyTriageService replyTriageService,
                                  ReplyComposerService replyComposerService, CollectionSchemaService collectionSchemaService,
                                  RecordService recordService, AiSettingsService aiSettingsService,
                                  ImportanceSignalService importanceSignalService, AiProperties properties,
                                  AiExecutors aiExecutors, PlatformTransactionManager transactionManager, Clock clock) {
        this.emailMessageRepository = emailMessageRepository;
        this.mailAccountRepository = mailAccountRepository;
        this.proposalRepository = proposalRepository;
        this.proposalService = proposalService;
        this.replyLifecycleService = replyLifecycleService;
        this.replyTriageService = replyTriageService;
        this.replyComposerService = replyComposerService;
        this.collectionSchemaService = collectionSchemaService;
        this.recordService = recordService;
        this.aiSettingsService = aiSettingsService;
        this.importanceSignalService = importanceSignalService;
        this.properties = properties;
        this.aiExecutors = aiExecutors;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Everything about one email the reply steps need, read in one short transaction. */
    private record Context(Long emailId, MailAccount.MailAccountId accountId, String conversationId, ReplyThread thread,
                           ReplyCandidateFilter.Candidate candidate, List<EmailRecipient> to, List<EmailRecipient> cc,
                           String senderName, String senderAddress, String subject, boolean pending) {
    }

    /**
     * Rules, triage, draft for one email the worker just processed. Returns whether a proposal was created. A newer
     * pending draft for the email or the rules end it without any model call.
     */
    public boolean suggestIfNeeded(Long emailId) {
        Context context = transactionTemplate.execute(status -> load(emailId).orElse(null));
        if (context == null || context.pending()) {
            return false;
        }
        Optional<ReplyCandidateFilter.Skip> skip = ReplyCandidateFilter.skip(context.candidate(), clock.instant());
        if (skip.isPresent()) {
            log.debug("Email {} gets no reply draft: {}", emailId, skip.get());
            return false;
        }
        ReplyTriageService.Triage triage = replyTriageService.triage(context.thread());
        if (!triage.needsReply()) {
            return false;
        }
        return composeAndSubmit(context, triage.reason(), triage.urgency());
    }

    /**
     * Queues a draft for the current user's email on the AI thread, without rules and triage (the user asked).
     *
     * @throws EmailNotFoundException              not one of the user's emails
     * @throws ReplySuggestionUnavailableException own sent mail, or mail access / reply drafts are off
     */
    public void requestSuggestion(Long emailId) {
        Long userId = CurrentActor.userId().orElseThrow(() -> new IllegalStateException("No authenticated user"));
        transactionTemplate.executeWithoutResult(status -> {
            EmailMessage email = emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId))
                    .filter(candidate -> candidate.getAccount().getOwner().getId().id().equals(userId))
                    .orElseThrow(() -> new EmailNotFoundException(emailId));
            if (EmailMessage.FOLDER_SENT.equals(email.getFolder())) {
                throw new ReplySuggestionUnavailableException("Your own sent messages get no reply drafts");
            }
            AiSettings settings = aiSettingsService.settingsFor(new AppUser.AppUserId(userId));
            if (!settings.isMailAccess() || settings.getReplies() == AiPermissionLevel.OFF) {
                throw new ReplySuggestionUnavailableException("Reply drafts are turned off in Settings → Autonomy");
            }
        });
        if (onDemand.put(emailId, ReplySuggestionState.RUNNING) == ReplySuggestionState.RUNNING) {
            return;
        }
        try {
            aiExecutors.analysis().execute(() -> runOnDemand(emailId));
        } catch (RejectedExecutionException e) {
            onDemand.remove(emailId);
            throw e;
        }
    }

    /** RUNNING while an on-demand suggestion for the email is queued or written, FAILED after a failed one. */
    public ReplySuggestionState state(Long emailId) {
        return onDemand.getOrDefault(emailId, ReplySuggestionState.IDLE);
    }

    private void runOnDemand(Long emailId) {
        try {
            Context context = transactionTemplate.execute(status -> load(emailId).orElse(null));
            if (context != null && composeAndSubmit(context, null, null)) {
                onDemand.remove(emailId);
            } else {
                onDemand.put(emailId, ReplySuggestionState.FAILED);
            }
        } catch (RuntimeException e) {
            log.warn("Suggesting a reply for email {} failed: {}", emailId, e.getMessage());
            onDemand.put(emailId, ReplySuggestionState.FAILED);
        }
    }

    /** @param urgency the triage's low/normal/high, {@code null} on demand (no triage) */
    private boolean composeAndSubmit(Context context, String triageReason, String urgency) {
        Optional<ReplyComposerService.ComposedReply> composed = replyComposerService.compose(context.thread(), grounding(context));
        if (composed.isEmpty()) {
            return false;
        }
        ReplyComposerService.ComposedReply reply = composed.get();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("emailId", context.emailId());
        payload.put("accountId", context.accountId().id());
        payload.put("conversationId", context.conversationId());
        payload.put("to", recipients(context.to()));
        payload.put("cc", recipients(context.cc()));
        payload.put("subject", reply.subject());
        payload.put("body", reply.body());
        payload.put("language", reply.language());
        payload.put("placeholders", reply.placeholders());
        String reasoning = !reply.summary().isBlank() ? reply.summary() : triageReason;
        ProposalDraft draft = new ProposalDraft(ProposalKind.DRAFT_REPLY, payload, null, reasoning)
                .withSignals(signals(context.emailId()).withUrgency(urgency));
        transactionTemplate.executeWithoutResult(status -> {
            replyLifecycleService.supersedeConversation(context.accountId(), context.conversationId(), ReplyLifecycleService.REPLACED);
            proposalService.submitDraft(draft, context.emailId(), AiPermissionLevel.SUGGEST, properties.models().extraction(),
                    ReplyComposerService.PROMPT_VERSION, List.of());
        });
        return true;
    }

    /** What is known about the sender; nothing when the lookup fails (the draft is kept either way). */
    private ImportanceSignals signals(Long emailId) {
        try {
            return importanceSignalService.signalsFor(emailId);
        } catch (RuntimeException e) {
            log.debug("No importance signals for email {}: {}", emailId, e.getMessage());
            return ImportanceSignals.NONE;
        }
    }

    private Optional<Context> load(Long emailId) {
        return emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId))
                .filter(email -> !EmailMessage.FOLDER_SENT.equals(email.getFolder()))
                .map(this::context);
    }

    private Context context(EmailMessage email) {
        MailAccount account = email.getAccount();
        Set<String> own = mailAccountRepository.findByOwner_Id(account.getOwner().getId()).stream()
                .map(mailbox -> mailbox.getEmailAddress() != null ? mailbox.getEmailAddress().value().toLowerCase(Locale.ROOT) : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (account.getEmailAddress() != null) {
            own.add(account.getEmailAddress().value().toLowerCase(Locale.ROOT));
        }
        List<EmailMessage> conversation = email.getConversationId() == null
                ? List.of(email)
                : emailMessageRepository.findThread(account.getId(), email.getConversationId());
        boolean answered = conversation.stream().anyMatch(message -> isSent(message) && message.getReceivedAt().isAfter(email.getReceivedAt()));
        boolean newer = conversation.stream().anyMatch(message -> !isSent(message) && !message.getId().equals(email.getId())
                && message.getReceivedAt().isAfter(email.getReceivedAt()));

        List<ReplyThread.Message> messages = new ArrayList<>();
        for (EmailMessage message : conversation) {
            if (!message.getId().equals(email.getId()) && !message.getReceivedAt().isAfter(email.getReceivedAt())) {
                messages.add(threadMessage(message));
            }
        }
        messages.add(threadMessage(email));
        String body = EmailTextCleaner.clean(AiAnalysisWorker.plainText(email), BODY_CHARS);
        List<String> to = Optional.ofNullable(email.getToRecipients()).orElse(List.of()).stream().map(EmailRecipient::address).toList();
        ReplyCandidateFilter.Candidate candidate = new ReplyCandidateFilter.Candidate(email.getFromAddress(), to, body,
                email.getReceivedAt(), own, answered, newer);

        String sender = Optional.ofNullable(email.getFromAddress()).orElse("");
        List<EmailRecipient> cc = Optional.ofNullable(email.getCcRecipients()).orElse(List.of()).stream()
                .filter(recipient -> recipient.address() != null && !own.contains(recipient.address().toLowerCase(Locale.ROOT))
                        && !recipient.address().equalsIgnoreCase(sender))
                .toList();
        boolean pending = !proposalRepository.findByKindAndStatusAndSourceEmail_IdIn(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING,
                List.of(email.getId())).isEmpty();
        ReplyThread thread = new ReplyThread(account.getDisplayName(),
                account.getEmailAddress() != null ? account.getEmailAddress().value() : null, messages);
        return new Context(email.getId().id(), account.getId(), email.getConversationId(), thread, candidate,
                List.of(new EmailRecipient(email.getFromName(), sender)), cc, email.getFromName(), sender, email.getSubject(), pending);
    }

    private static boolean isSent(EmailMessage message) {
        return EmailMessage.FOLDER_SENT.equals(message.getFolder());
    }

    private static ReplyThread.Message threadMessage(EmailMessage message) {
        return ThreadMessages.of(message);
    }

    private static List<Map<String, Object>> recipients(List<EmailRecipient> recipients) {
        return recipients.stream().map(recipient -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", recipient.name());
            map.put("address", recipient.address());
            return map;
        }).toList();
    }

    /**
     * What the user's records say about the sender and the topic: the sender's contact and organization, and up to
     * {@link #MAX_GROUNDING_RECORDS} upcoming appointments and open to-dos that mention the sender or the subject.
     */
    private List<String> grounding(Context context) {
        Map<String, CollectionDto> bySlug = collectionSchemaService.findAll().stream()
                .collect(Collectors.toMap(CollectionDto::getSlug, collection -> collection, (a, b) -> a));
        List<String> lines = new ArrayList<>();
        String address = context.senderAddress();
        if (bySlug.containsKey("people") && !address.isBlank()) {
            records(bySlug.get("people"), List.of("email:eq:" + address), null, null, 1).forEach(person -> {
                Object role = person.getValues() != null ? person.getValues().get("role_title") : null;
                lines.add("The sender in your contacts: " + person.getTitle() + (role != null ? ", " + role : ""));
            });
        }
        int at = address.indexOf('@');
        if (bySlug.containsKey("organizations") && at > 0) {
            records(bySlug.get("organizations"), List.of("domain:eq:" + address.substring(at + 1)), null, null, 1)
                    .forEach(organization -> lines.add("The sender's organization: " + organization.getTitle()));
        }

        List<String> terms = new ArrayList<>();
        if (context.senderName() != null && !context.senderName().isBlank()) terms.add(context.senderName().strip());
        String topic = Optional.ofNullable(context.subject()).orElse("").replaceAll("(?i)^((re|aw|antw|wg|fw|fwd)\\s*:\\s*)+", "").strip();
        if (topic.length() >= 4) terms.add(topic);
        Map<String, String> related = new LinkedHashMap<>();
        for (String term : terms) {
            if (bySlug.containsKey("events")) {
                records(bySlug.get("events"), List.of("starts_at:gte:" + clock.instant()), term, "starts_at", MAX_GROUNDING_RECORDS)
                        .forEach(event -> related.putIfAbsent("event " + event.getId(), "Upcoming appointment: " + event.getTitle()
                                + Optional.ofNullable(event.getValues()).map(values -> values.get("starts_at")).map(start -> " – " + start).orElse("")));
            }
            if (bySlug.containsKey("tasks")) {
                records(bySlug.get("tasks"), List.of("state:ne:done"), term, null, MAX_GROUNDING_RECORDS)
                        .forEach(task -> related.putIfAbsent("task " + task.getId(), "Open to-do: " + task.getTitle()
                                + Optional.ofNullable(task.getValues()).map(values -> values.get("due_date")).map(due -> " – due " + due).orElse("")));
            }
        }
        related.values().stream().limit(MAX_GROUNDING_RECORDS).forEach(lines::add);
        return lines;
    }

    /** Records matching the filters, or none when the collection lacks a filtered field (user-changed schema). */
    private List<RecordDto> records(CollectionDto collection, List<String> filters, String query, String sort, int size) {
        try {
            return recordService.listRecords(collection.getId(), filters, query, sort, 0, size).getItems();
        } catch (RuntimeException e) {
            log.debug("No reply grounding from collection {}: {}", collection.getSlug(), e.getMessage());
            return List.of();
        }
    }
}
