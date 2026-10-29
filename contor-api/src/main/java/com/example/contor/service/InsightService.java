package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.DigestEmail;
import com.example.contor.ai.EmailTextCleaner;
import com.example.contor.ai.ImportanceSignals;
import com.example.contor.ai.Insight;
import com.example.contor.ai.InsightDetectors;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.InsightRunDto;
import com.example.contor.api.model.RecordDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AnalysisRunStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ProposalKind;
import com.example.contor.persistence.AiAnalysisRunRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.persistence.RecordSourceRepository;
import com.example.contor.security.CurrentActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Insights (AI v2 step 8): what only shows across several emails, proposed as INSIGHT proposals with follow-up to-dos
 * and appointments. Rules first ({@link InsightDetectors}: unanswered questions, overlapping appointments), then one
 * model pass over the last two weeks ({@link InsightModelService}). At most {@link #MAX_PER_RUN} per run, each key only
 * once ever (also after it was dismissed). Runs daily for users with mail access whose to-dos or calendar the assistant
 * may fill, never during an analysis run, and on request. Always suggestions: approving an insight is the user's call.
 */
@Slf4j
@Service
public class InsightService {

    static final int MAX_PER_RUN = 5;
    private static final Duration DIGEST_WINDOW = Duration.ofDays(14);
    private static final Duration QUESTION_WINDOW = Duration.ofDays(21);
    private static final Duration UPCOMING = Duration.ofDays(30);
    private static final int DIGEST_EMAILS = 200;
    private static final int RECORD_LINES = 20;
    private static final int BODY_CHARS = 2000;
    private static final String TASKS = "tasks";
    private static final String EVENTS = "events";

    private final EmailMessageRepository emailMessageRepository;
    private final ProposalRepository proposalRepository;
    private final ProposalService proposalService;
    private final CollectionSchemaService collectionSchemaService;
    private final RecordService recordService;
    private final RecordSourceRepository recordSourceRepository;
    private final AiSettingsService aiSettingsService;
    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final InsightModelService insightModelService;
    private final AiProperties properties;
    private final AiExecutors aiExecutors;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final AtomicBoolean scheduledRunning = new AtomicBoolean(false);
    /** Users whose on-demand run is queued or running (one app instance only). */
    private final Set<Long> requested = ConcurrentHashMap.newKeySet();

    public InsightService(EmailMessageRepository emailMessageRepository, ProposalRepository proposalRepository, ProposalService proposalService,
                          CollectionSchemaService collectionSchemaService, RecordService recordService,
                          RecordSourceRepository recordSourceRepository, AiSettingsService aiSettingsService,
                          AiAnalysisRunRepository aiAnalysisRunRepository, InsightModelService insightModelService, AiProperties properties,
                          AiExecutors aiExecutors, PlatformTransactionManager transactionManager, Clock clock) {
        this.emailMessageRepository = emailMessageRepository;
        this.proposalRepository = proposalRepository;
        this.proposalService = proposalService;
        this.collectionSchemaService = collectionSchemaService;
        this.recordService = recordService;
        this.recordSourceRepository = recordSourceRepository;
        this.aiSettingsService = aiSettingsService;
        this.aiAnalysisRunRepository = aiAnalysisRunRepository;
        this.insightModelService = insightModelService;
        this.properties = properties;
        this.aiExecutors = aiExecutors;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Daily pass for every onboarded user (on the AI thread, after whatever it is doing). */
    @Scheduled(fixedDelayString = "${app.ai.insights.interval:24h}", initialDelayString = "${app.ai.insights.initial-delay:15m}")
    public void runDaily() {
        if (!properties.processing().enabled() || !scheduledRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            aiExecutors.analysis().execute(() -> {
                try {
                    aiSettingsService.onboardedUsers().forEach(this::runSafely);
                } finally {
                    scheduledRunning.set(false);
                }
            });
        } catch (RuntimeException e) {
            scheduledRunning.set(false);
            throw e;
        }
    }

    /** Queues a pass for the current user ("Look for insights"); already queued is fine. */
    public InsightRunDto requestInsights() {
        requestRun();
        return new InsightRunDto(true);
    }

    /** Whether the current user's requested pass is still queued or running. */
    public InsightRunDto getInsightRun() {
        return new InsightRunDto(isRunning());
    }

    /** Queues a pass for the current user on the AI thread; false when one is already queued. */
    public boolean requestRun() {
        Long userId = CurrentActor.userId().orElseThrow(() -> new IllegalStateException("No authenticated user"));
        if (!requested.add(userId)) {
            return false;
        }
        try {
            aiExecutors.analysis().execute(() -> {
                try {
                    runSafely(new AppUser.AppUserId(userId));
                } finally {
                    requested.remove(userId);
                }
            });
        } catch (RuntimeException e) {
            requested.remove(userId);
            throw e;
        }
        return true;
    }

    /** Whether a requested pass of the current user is queued or running. */
    public boolean isRunning() {
        return CurrentActor.userId().map(requested::contains).orElse(false);
    }

    private void runSafely(AppUser.AppUserId owner) {
        try {
            int proposed = runFor(owner);
            log.info("Insight pass for user {}: {} proposed", owner.id(), proposed);
        } catch (RuntimeException e) {
            log.warn("Insight pass for user {} failed: {}", owner.id(), e.getMessage());
        }
    }

    /** Finds and proposes a user's insights; returns how many were proposed. */
    public int runFor(AppUser.AppUserId owner) {
        boolean analysing = Boolean.TRUE.equals(transactions.execute(status ->
                aiAnalysisRunRepository.existsByUser_IdAndStatusIn(owner, AnalysisRunStatus.ACTIVE)));
        if (analysing) {
            return 0;
        }
        AiSettings settings = aiSettingsService.settingsFor(owner);
        if (!settings.isMailAccess() || (!settings.getTasks().allowed() && !settings.getCalendar().allowed())) {
            return 0;
        }
        Map<String, Long> collections = new HashMap<>();
        collectionSchemaService.findAll().forEach(collection -> collections.put(collection.getSlug(), collection.getId()));
        Instant now = clock.instant();

        List<Insight> candidates = new ArrayList<>(unansweredQuestions(owner, now));
        candidates.addAll(clashes(collections.get(EVENTS), now));
        try {
            List<DigestEmail> digest = transactions.execute(status ->
                    emailMessageRepository.findDigest(owner, now.minus(DIGEST_WINDOW), PageRequest.of(0, DIGEST_EMAILS)));
            candidates.addAll(insightModelService.findInsights(digest != null ? digest : List.of(),
                    openTasks(collections.get(TASKS)), upcoming(collections.get(EVENTS), now)));
        } catch (RuntimeException e) {
            log.warn("The insight model pass failed, keeping the rule insights: {}", e.getMessage());
        }

        Set<String> seen = new LinkedHashSet<>();
        int proposed = 0;
        for (Insight insight : candidates) {
            if (proposed == MAX_PER_RUN) {
                break;
            }
            if (!seen.add(insight.key()) || proposalRepository.existsInsightKey(insight.key())) {
                continue;
            }
            proposalService.submitDraft(draft(insight, settings, collections), insight.evidenceEmailIds().stream().findFirst().orElse(null),
                    AiPermissionLevel.SUGGEST, insight.source().startsWith("rule") ? "rules" : properties.models().extraction(),
                    insight.source().startsWith("rule") ? insight.source() : InsightModelService.PROMPT_VERSION, List.of());
            proposed++;
        }
        return proposed;
    }

    /** The proposal; follow-ups into a collection that is missing or the assistant may not fill are left out. */
    private static ProposalDraft draft(Insight insight, AiSettings settings, Map<String, Long> collections) {
        List<Map<String, Object>> followUps = new ArrayList<>();
        for (Insight.FollowUp followUp : insight.followUps()) {
            AiPermissionLevel level = TASKS.equals(followUp.collection()) ? settings.getTasks() : settings.getCalendar();
            Long collectionId = collections.get(followUp.collection());
            if (collectionId == null || !level.allowed()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("collectionId", collectionId);
            entry.put("title", followUp.title());
            entry.put("values", new LinkedHashMap<>(followUp.values()));
            followUps.add(entry);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("insightKey", insight.key());
        payload.put("title", insight.title());
        payload.put("observation", insight.observation());
        payload.put("whyItMatters", insight.whyItMatters());
        payload.put("evidenceEmailIds", insight.evidenceEmailIds());
        payload.put("followUps", followUps);
        payload.put("source", insight.source());
        return new ProposalDraft(ProposalKind.INSIGHT, payload, insight.confidence(), insight.observation()).withSignals(ImportanceSignals.NONE);
    }

    private List<Insight> unansweredQuestions(AppUser.AppUserId owner, Instant now) {
        List<InsightDetectors.SentMail> sent = transactions.execute(status -> {
            List<Long> ids = emailMessageRepository.findUnansweredSent(owner, now.minus(QUESTION_WINDOW), now.minus(InsightDetectors.ANSWER_WAIT), 20);
            return emailMessageRepository.findAllById(ids.stream().map(EmailMessage.EmailMessageId::new).toList()).stream()
                    .map(email -> {
                        EmailRecipient to = Optional.ofNullable(email.getToRecipients()).orElse(List.of()).stream().findFirst().orElse(null);
                        return new InsightDetectors.SentMail(email.getId().id(), to != null ? to.name() : null, to != null ? to.address() : null,
                                email.getSubject(), EmailTextCleaner.clean(AiAnalysisWorker.plainText(email), BODY_CHARS), email.getReceivedAt());
                    }).toList();
        });
        List<Insight> insights = new ArrayList<>();
        for (InsightDetectors.SentMail mail : sent != null ? sent : List.<InsightDetectors.SentMail>of()) {
            InsightDetectors.unansweredQuestion(mail, now, properties.timezone()).ifPresent(insights::add);
        }
        return insights;
    }

    private List<Insight> clashes(Long eventsId, Instant now) {
        if (eventsId == null) {
            return List.of();
        }
        List<InsightDetectors.Appointment> appointments = new ArrayList<>();
        for (RecordDto event : records(eventsId, List.of("starts_at:gte:" + now, "starts_at:lt:" + now.plus(UPCOMING)), "starts_at", 200)) {
            Map<String, Object> values = Optional.ofNullable(event.getValues()).orElse(Map.of());
            if (Boolean.TRUE.equals(values.get("all_day")) || !(values.get("starts_at") instanceof String start)) {
                continue;
            }
            try {
                Instant starts = OffsetDateTime.parse(start).toInstant();
                Instant ends = values.get("ends_at") instanceof String end ? OffsetDateTime.parse(end).toInstant() : starts.plus(Duration.ofHours(1));
                List<Long> emails = transactions.execute(status -> recordSourceRepository
                        .findByCollection_IdAndRecordId(new Collection.CollectionId(eventsId), event.getId()).stream()
                        .filter(source -> source.getEmail() != null).map(source -> source.getEmail().getId().id()).distinct().toList());
                appointments.add(new InsightDetectors.Appointment(event.getId(), event.getTitle(), starts, ends, emails != null ? emails : List.of()));
            } catch (DateTimeParseException e) {
                log.debug("Appointment {} has an unreadable date", event.getId());
            }
        }
        return InsightDetectors.clashes(appointments, properties.timezone());
    }

    private List<String> openTasks(Long tasksId) {
        if (tasksId == null) {
            return List.of();
        }
        return records(tasksId, List.of("state:ne:done"), null, RECORD_LINES).stream()
                .map(task -> task.getTitle() + Optional.ofNullable(task.getValues()).map(values -> values.get("due_date"))
                        .map(due -> " (due " + due + ")").orElse(""))
                .toList();
    }

    private List<String> upcoming(Long eventsId, Instant now) {
        if (eventsId == null) {
            return List.of();
        }
        return records(eventsId, List.of("starts_at:gte:" + now, "starts_at:lt:" + now.plus(UPCOMING)), "starts_at", RECORD_LINES).stream()
                .map(event -> event.getTitle() + Optional.ofNullable(event.getValues()).map(values -> values.get("starts_at"))
                        .map(start -> " – " + start).orElse(""))
                .toList();
    }

    /** Records of a collection, or none when its schema lacks a filtered field (user-changed system collection). */
    private List<RecordDto> records(Long collectionId, List<String> filters, String sort, int size) {
        try {
            List<RecordDto> items = recordService.listRecords(collectionId, filters, null, sort, 0, size).getItems();
            return items != null ? items : List.of();
        } catch (RuntimeException e) {
            log.debug("No records of collection {} for insights: {}", collectionId, e.getMessage());
            return List.of();
        }
    }
}
