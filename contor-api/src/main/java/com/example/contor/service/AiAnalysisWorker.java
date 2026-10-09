package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.DiscoveryEmail;
import com.example.contor.ai.EmailContent;
import com.example.contor.ai.ExtractionService;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AiAnalysisRun;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AnalysisRunStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.ProposalKind;
import com.example.contor.persistence.AiAnalysisRunRepository;
import com.example.contor.persistence.EmailMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs inbox analyses on the single AI thread ({@link AiExecutors#analysis()}):
 * {@code QUEUED → DISCOVERING → (AWAITING_REVIEW) → EXTRACTING → COMPLETED}. Model calls run outside transactions;
 * every database step is its own short transaction, so progress is visible while a run is active and a cancel (from
 * the API) is noticed before the next email. Also processes new mail of onboarded users on a schedule and resumes
 * runs interrupted by a restart.
 */
@Slf4j
@Component
public class AiAnalysisWorker {

    /** Stored in {@code email_message.processing_version}; bump when the extraction changes enough to reprocess. */
    public static final int PROCESSING_VERSION = 1;

    /** Emails read for category discovery (the discovery service samples them further). */
    private static final int DISCOVERY_EMAILS = 2000;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final ObjectMapper PAYLOAD_MAPPER = JsonMapper.builder().build();

    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final AiSettingsService aiSettingsService;
    private final CollectionSchemaService collectionSchemaService;
    private final InboxDiscoveryService inboxDiscoveryService;
    private final ExtractionService extractionService;
    private final ProposalService proposalService;
    private final AiProperties properties;
    private final AiExecutors aiExecutors;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final AtomicBoolean processingNewMail = new AtomicBoolean(false);

    /** Counts what happened to one email's drafts. */
    private record EmailResult(int suggested, int autoApplied, boolean failed) {
    }

    public AiAnalysisWorker(AiAnalysisRunRepository aiAnalysisRunRepository, EmailMessageRepository emailMessageRepository,
                            AiSettingsService aiSettingsService, CollectionSchemaService collectionSchemaService,
                            InboxDiscoveryService inboxDiscoveryService, ExtractionService extractionService,
                            ProposalService proposalService, AiProperties properties, AiExecutors aiExecutors,
                            PlatformTransactionManager transactionManager, Clock clock) {
        this.aiAnalysisRunRepository = aiAnalysisRunRepository;
        this.emailMessageRepository = emailMessageRepository;
        this.aiSettingsService = aiSettingsService;
        this.collectionSchemaService = collectionSchemaService;
        this.inboxDiscoveryService = inboxDiscoveryService;
        this.extractionService = extractionService;
        this.proposalService = proposalService;
        this.properties = properties;
        this.aiExecutors = aiExecutors;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Runs (or continues) the run on the AI thread. */
    public void submit(Long runId) {
        aiExecutors.analysis().execute(() -> run(runId));
    }

    public void run(Long runId) {
        AiAnalysisRun run = load(runId);
        if (run == null || !(run.getStatus() == AnalysisRunStatus.QUEUED || run.getStatus() == AnalysisRunStatus.EXTRACTING)) {
            return;
        }
        AppUser.AppUserId userId = run.getUser().getId();
        try {
            AiSettings settings = aiSettingsService.settingsFor(userId);
            if (run.getStatus() == AnalysisRunStatus.QUEUED && !discover(runId, userId, run.getSince(), settings)) {
                return;
            }
            extract(runId, userId, run.getSince(), settings);
        } catch (RuntimeException e) {
            log.warn("Analysis run {} failed: {}", runId, e.getMessage());
            update(runId, failed -> {
                failed.setStatus(AnalysisRunStatus.FAILED);
                failed.setCurrentEmail(null);
                failed.setError(truncate(e.getMessage()));
                failed.setFinishedAt(clock.instant());
            });
        }
    }

    /** Returns whether extraction may start right away (false: waiting for review, or cancelled). */
    private boolean discover(Long runId, AppUser.AppUserId userId, Instant since, AiSettings settings) {
        update(runId, run -> {
            run.setStatus(AnalysisRunStatus.DISCOVERING);
            run.setStartedAt(clock.instant());
        });
        if (!settings.getCategories().allowed()) {
            return startExtracting(runId);
        }

        List<DiscoveryEmail> emails = transactionTemplate.execute(status -> emailMessageRepository.findDiscoveryEmails(
                userId, since != null ? since : Instant.EPOCH, PageRequest.of(0, DISCOVERY_EMAILS)));
        List<InboxDiscoveryService.Suggestion> suggestions = inboxDiscoveryService.discover(emails, collectionSchemaService.findAll(),
                (done, total) -> update(runId, run -> {
                    run.setDiscoverySteps(total);
                    run.setDiscoveryStepsDone(done);
                }));

        int created = 0;
        int suggested = 0;
        for (InboxDiscoveryService.Suggestion suggestion : suggestions) {
            if (settings.getCategories() == AiPermissionLevel.AUTO) {
                try {
                    collectionSchemaService.createAiCollection(suggestion.command());
                    created++;
                } catch (RuntimeException e) {
                    log.warn("Could not create discovered collection '{}': {}", suggestion.command().getSlug(), e.getMessage());
                }
            } else {
                proposalService.submitDraft(collectionDraft(suggestion), null, AiPermissionLevel.SUGGEST,
                        properties.models().routing(), InboxDiscoveryService.PROMPT_VERSION);
                suggested++;
            }
        }
        int discovered = created + suggested;
        int review = suggested;
        update(runId, run -> {
            run.setCategoriesDiscovered(discovered);
            run.setProposalsCreated(run.getProposalsCreated() + review);
        });
        if (suggested > 0) {
            update(runId, run -> {
                if (run.getStatus() == AnalysisRunStatus.DISCOVERING) run.setStatus(AnalysisRunStatus.AWAITING_REVIEW);
            });
            return false;
        }
        return startExtracting(runId);
    }

    private boolean startExtracting(Long runId) {
        AiAnalysisRun run = update(runId, current -> {
            if (current.getStatus().isActive()) current.setStatus(AnalysisRunStatus.EXTRACTING);
        });
        return run != null && run.getStatus() == AnalysisRunStatus.EXTRACTING;
    }

    private void extract(Long runId, AppUser.AppUserId userId, Instant since, AiSettings settings) {
        Instant from = since != null ? since : Instant.EPOCH;
        List<Long> emailIds = transactionTemplate.execute(status ->
                emailMessageRepository.findIdsForProcessing(userId, ProcessingState.NEW, from, PageRequest.of(0, Integer.MAX_VALUE)));
        update(runId, run -> {
            run.setTotalEmails(run.getProcessedEmails() + emailIds.size());
            if (run.getStartedAt() == null) run.setStartedAt(clock.instant());
        });
        List<CollectionDto> targets = targets(settings);

        for (Long emailId : emailIds) {
            AiAnalysisRun current = load(runId);
            if (current == null || current.getStatus() != AnalysisRunStatus.EXTRACTING) {
                return;
            }
            update(runId, run -> run.setCurrentEmail(emailMessageRepository.getReferenceById(new EmailMessage.EmailMessageId(emailId))));
            EmailResult result = processEmail(emailId, settings, targets);
            update(runId, run -> {
                run.setCurrentEmail(null);
                run.setProcessedEmails(run.getProcessedEmails() + 1);
                run.setProposalsCreated(run.getProposalsCreated() + result.suggested());
                run.setAutoApplied(run.getAutoApplied() + result.autoApplied());
                if (result.failed()) run.setFailedEmails(run.getFailedEmails() + 1);
            });
        }

        AiAnalysisRun finished = update(runId, run -> {
            if (run.getStatus() == AnalysisRunStatus.EXTRACTING) {
                run.setStatus(AnalysisRunStatus.COMPLETED);
                run.setFinishedAt(clock.instant());
            }
        });
        if (finished != null && finished.getStatus() == AnalysisRunStatus.COMPLETED && since != null) {
            // Older mail is outside the window the user chose; background processing must not pick it up later.
            transactionTemplate.executeWithoutResult(status ->
                    emailMessageRepository.updateProcessingStateBefore(userId, ProcessingState.NEW, ProcessingState.SKIPPED, since));
        }
    }

    /** Analyzes one email and submits its drafts; never throws (a failure marks the email FAILED). */
    private EmailResult processEmail(Long emailId, AiSettings settings, List<CollectionDto> targets) {
        EmailContent content = transactionTemplate.execute(status -> emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId))
                .map(email -> {
                    email.setProcessingState(ProcessingState.PROCESSING);
                    emailMessageRepository.saveAndFlush(email);
                    return content(email);
                })
                .orElse(null));
        if (content == null) {
            return new EmailResult(0, 0, false);
        }
        try {
            int suggested = 0;
            int autoApplied = 0;
            Map<Long, String> slugs = new HashMap<>();
            targets.forEach(target -> slugs.put(target.getId(), target.getSlug()));
            Map<String, Long> proposalIds = new HashMap<>();
            for (ProposalDraft draft : extractionService.analyze(content, targets)) {
                // relations follow the records permission; a draft pointing at something that was not stored is dropped
                AiPermissionLevel level = draft.kind() == ProposalKind.CREATE_RELATION
                        ? settings.getRecords()
                        : levelFor(settings, slugs.get(collectionId(draft)));
                if (!level.allowed()) {
                    continue;
                }
                Optional<ResolvedDraft> resolved = resolve(draft, proposalIds);
                if (resolved.isEmpty()) {
                    continue;
                }
                ProposalService.Submission submission = submitDraft(resolved.get().draft(), emailId, level, resolved.get().dependsOn());
                if (draft.ref() != null && submission.proposalId() != null) {
                    proposalIds.put(draft.ref(), submission.proposalId());
                }
                if (submission.outcome() == ProposalService.Outcome.AUTO_APPLIED) autoApplied++;
                else suggested++;
            }
            markEmail(emailId, ProcessingState.DONE, null);
            return new EmailResult(suggested, autoApplied, false);
        } catch (RuntimeException e) {
            log.warn("AI processing of email {} failed: {}", emailId, e.getMessage());
            markEmail(emailId, ProcessingState.FAILED, truncate(e.getMessage()));
            return new EmailResult(0, 0, true);
        }
    }

    /** A draft whose local refs became proposal ids, with the proposals it has to wait for. */
    private record ResolvedDraft(ProposalDraft draft, List<Long> dependsOn) {
    }

    /** Replaces {@code {"ref": x}} by {@code {"proposalId": id}}; empty when a ref has no stored proposal. */
    private static Optional<ResolvedDraft> resolve(ProposalDraft draft, Map<String, Long> proposalIds) {
        if (draft.dependsOnRefs().isEmpty() && draft.dependsOnProposalIds().isEmpty()) {
            return Optional.of(new ResolvedDraft(draft, List.of()));
        }
        Set<Long> dependsOn = new LinkedHashSet<>(draft.dependsOnProposalIds());
        for (String ref : draft.dependsOnRefs()) {
            Long id = proposalIds.get(ref);
            if (id == null) {
                return Optional.empty();
            }
            dependsOn.add(id);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) replaceRefs(draft.payload(), proposalIds);
        return Optional.of(new ResolvedDraft(new ProposalDraft(draft.kind(), payload, draft.confidence(), draft.reasoning(),
                draft.ref(), List.of(), List.of()), List.copyOf(dependsOn)));
    }

    private static Object replaceRefs(Object value, Map<String, Long> proposalIds) {
        if (value instanceof Map<?, ?> map) {
            if (map.size() == 1 && map.get("ref") instanceof String ref && proposalIds.containsKey(ref)) {
                return Map.of("proposalId", proposalIds.get(ref));
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), replaceRefs(nested, proposalIds)));
            return copy;
        }
        return value;
    }

    /** AUTO falls back to a suggestion when applying fails (e.g. a value the collection rejects), so nothing is lost. */
    private ProposalService.Submission submitDraft(ProposalDraft draft, Long emailId, AiPermissionLevel level, List<Long> dependsOn) {
        String model = properties.models().extraction();
        if (level == AiPermissionLevel.AUTO) {
            try {
                return proposalService.submitDraft(draft, emailId, AiPermissionLevel.AUTO, model, EmailExtractionService.PROMPT_VERSION, dependsOn);
            } catch (RuntimeException e) {
                log.info("Auto-applying a {} from email {} failed, keeping it as a suggestion: {}", draft.kind(), emailId, e.getMessage());
            }
        }
        return proposalService.submitDraft(draft, emailId, AiPermissionLevel.SUGGEST, model, EmailExtractionService.PROMPT_VERSION, dependsOn);
    }

    /** Processes new mail of users who completed the AI setup (one batch per user and pass, never during a run). */
    @Scheduled(fixedDelayString = "${app.ai.processing.interval:2m}", initialDelayString = "${app.ai.processing.interval:2m}")
    public void processNewMail() {
        if (!properties.processing().enabled() || !processingNewMail.compareAndSet(false, true)) {
            return;
        }
        try {
            aiExecutors.analysis().execute(() -> {
                try {
                    for (AppUser.AppUserId userId : aiSettingsService.onboardedUsers()) {
                        processPending(userId);
                    }
                } finally {
                    processingNewMail.set(false);
                }
            });
        } catch (RuntimeException e) {
            processingNewMail.set(false);
            throw e;
        }
    }

    private void processPending(AppUser.AppUserId userId) {
        boolean running = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                aiAnalysisRunRepository.existsByUser_IdAndStatusIn(userId, AnalysisRunStatus.ACTIVE)));
        if (running) {
            return;
        }
        List<Long> emailIds = transactionTemplate.execute(status -> emailMessageRepository.findIdsForProcessing(
                userId, ProcessingState.NEW, Instant.EPOCH, PageRequest.of(0, properties.processing().batchSize())));
        if (emailIds == null || emailIds.isEmpty()) {
            return;
        }
        AiSettings settings = aiSettingsService.settingsFor(userId);
        List<CollectionDto> targets = targets(settings);
        emailIds.forEach(emailId -> processEmail(emailId, settings, targets));
    }

    /** After a restart: emails left PROCESSING go back to NEW, interrupted runs continue. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedRuns() {
        List<Long> runIds = transactionTemplate.execute(status -> {
            emailMessageRepository.updateProcessingState(ProcessingState.PROCESSING, ProcessingState.NEW);
            List<Long> resumable = new ArrayList<>();
            for (AiAnalysisRun run : aiAnalysisRunRepository.findByStatusIn(AnalysisRunStatus.ACTIVE)) {
                if (run.getStatus() == AnalysisRunStatus.DISCOVERING) {
                    run.setStatus(AnalysisRunStatus.QUEUED);
                }
                if (run.getStatus() != AnalysisRunStatus.AWAITING_REVIEW) {
                    resumable.add(run.getId().id());
                }
            }
            return resumable;
        });
        if (runIds != null) {
            runIds.forEach(this::submit);
        }
    }

    /** Collections the AI may write records to under {@code settings}, in name order. */
    private List<CollectionDto> targets(AiSettings settings) {
        if (!settings.isMailAccess()) {
            return List.of();
        }
        return collectionSchemaService.findAll().stream()
                .filter(collection -> levelFor(settings, collection.getSlug()).allowed())
                .toList();
    }

    /** events → calendar, tasks → tasks, every other collection → records. */
    private static AiPermissionLevel levelFor(AiSettings settings, String slug) {
        if (slug == null) return AiPermissionLevel.OFF;
        return switch (slug) {
            case "events" -> settings.getCalendar();
            case "tasks" -> settings.getTasks();
            default -> settings.getRecords();
        };
    }

    private static Long collectionId(ProposalDraft draft) {
        return draft.payload().get("collectionId") instanceof Number number ? number.longValue() : null;
    }

    private static ProposalDraft collectionDraft(InboxDiscoveryService.Suggestion suggestion) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = PAYLOAD_MAPPER.convertValue(suggestion.command(), Map.class);
        return new ProposalDraft(ProposalKind.CREATE_COLLECTION, payload, null, suggestion.reason());
    }

    private EmailContent content(EmailMessage email) {
        List<String> to = Optional.ofNullable(email.getToRecipients()).orElse(List.of()).stream()
                .map(EmailRecipient::address)
                .toList();
        String mailbox = email.getAccount() != null && email.getAccount().getEmailAddress() != null
                ? email.getAccount().getEmailAddress().value()
                : null;
        return new EmailContent(email.getId().id(), mailbox, email.getFromName(), email.getFromAddress(), to,
                email.getSubject(), email.getReceivedAt(), plainText(email));
    }

    /** The text body, or the HTML body reduced to text. */
    static String plainText(EmailMessage email) {
        if (email.getBodyText() != null && !email.getBodyText().isBlank()) {
            return email.getBodyText();
        }
        String html = Optional.ofNullable(email.getBodyHtml()).orElse(Optional.ofNullable(email.getSnippet()).orElse(""));
        return html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</(p|div|tr|li|h[1-6])>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .strip();
    }

    private void markEmail(Long emailId, ProcessingState state, String error) {
        transactionTemplate.executeWithoutResult(status -> emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId))
                .ifPresent(email -> {
                    email.setProcessingState(state);
                    email.setProcessingError(error);
                    email.setProcessingVersion(PROCESSING_VERSION);
                    emailMessageRepository.saveAndFlush(email);
                }));
    }

    private AiAnalysisRun load(Long runId) {
        return transactionTemplate.execute(status ->
                aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(runId)).orElse(null));
    }

    /** Applies {@code change} to the freshly loaded run in its own transaction (only changed columns are written). */
    private AiAnalysisRun update(Long runId, Consumer<AiAnalysisRun> change) {
        return transactionTemplate.execute(status -> aiAnalysisRunRepository.findById(new AiAnalysisRun.AiAnalysisRunId(runId))
                .map(run -> {
                    change.accept(run);
                    return aiAnalysisRunRepository.saveAndFlush(run);
                })
                .orElse(null));
    }

    private static String truncate(String message) {
        String text = message != null ? message : "Unknown error";
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
