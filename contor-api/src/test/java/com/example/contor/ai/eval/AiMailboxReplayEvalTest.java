package com.example.contor.ai.eval;

import com.example.contor.MutableClock;
import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.NewRelationTypeCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.RelationTypeDto;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AiSettingsRepository;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.service.AiAnalysisWorker;
import com.example.contor.service.CollectionSchemaService;
import com.example.contor.service.DemoMailboxService;
import com.example.contor.service.InsightService;
import com.example.contor.service.RecordService;
import com.example.contor.service.RelationService;
import com.example.contor.service.RelationTypeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mailbox replay (business dataset, step 4): the whole business mailbox goes through the real pipeline in time order —
 * one owner with the two accounts of the dataset, the business collections and relation types, records, calendar and
 * to-dos on AUTO, replies (with courtesy replies) on SUGGEST. Each message is stored when it "arrives" (the clock follows
 * the mailbox) and the real {@link AiAnalysisWorker} processes it (embedding, context, extraction, proposals, replies);
 * {@link InsightService} runs every Friday evening. At the end the collections are compared with
 * {@code expected/end-state.json} ({@link EndStateComparison}: precision/recall, fields, relations, duplicates such as the
 * shared driver stored twice), the insights with {@code expected/insights.json}, and the reply proposals with the reply
 * labels. Report only, written to {@code target/ai-eval/replay-report.md}.
 * <p>
 * Needs Docker (Testcontainers) and Ollama with the extraction and the embedding model; about 10 s per mail on an RTX
 * 3060 (the 4-week slice ≈ 45 min). {@code -Dai.eval.replay.limit=n} replays only the first n days; the expected state is
 * then cut to the records first labelled in those days (their values may still include later changes).
 */
@Tag("ai-eval")
@SpringBootTest(properties = {"app.ai.processing.enabled=true", "app.ai.processing.interval=1000d", "app.ai.insights.initial-delay=1000d",
        "app.mail.sync.interval=1000d"})
@Import({PostgresTestcontainersConfiguration.class, AiMailboxReplayEvalTest.ReplayClock.class})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiMailboxReplayEvalTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(10);
    private static final Map<String, String[]> RELATION_TYPES = new LinkedHashMap<>();

    static {
        RELATION_TYPES.put("supplies", new String[] {"supplies", "supplied by", "suppliers", "purchase_orders"});
        RELATION_TYPES.put("ships", new String[] {"ships", "shipped in", "shipments", "sales_orders"});
        RELATION_TYPES.put("delivers", new String[] {"delivers", "delivered in", "shipments", "purchase_orders"});
        RELATION_TYPES.put("drives_for", new String[] {"drives for", "driven for by", "people", "organizations"});
    }

    @TestConfiguration
    static class ReplayClock {

        @Bean
        @Primary
        MutableClock replayClock() {
            return new MutableClock(Instant.parse("2026-07-05T22:00:00Z"));
        }
    }

    @Autowired private MutableClock clock;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private AiSettingsRepository aiSettingsRepository;
    @Autowired private MailAccountRepository mailAccountRepository;
    @Autowired private EmailMessageRepository emailMessageRepository;
    @Autowired private ProposalRepository proposalRepository;
    @Autowired private CollectionSchemaService collectionSchemaService;
    @Autowired private RelationTypeService relationTypeService;
    @Autowired private RecordService recordService;
    @Autowired private RelationService relationService;
    @Autowired private AiAnalysisWorker worker;
    @Autowired private InsightService insightService;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void the_mailbox_replayed_through_the_pipeline_ends_in_the_expected_state() throws IOException {
        int limitDays = Integer.parseInt(System.getProperty("ai.eval.replay.limit", "100000"));
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        List<BusinessMessage> all = BusinessCorpus.messages();
        LocalDate first = all.getFirst().email().receivedAt().atZone(ZONE).toLocalDate();
        Instant cutoff = first.plusDays(limitDays).atStartOfDay(ZONE).toInstant();
        List<BusinessMessage> replayed = all.stream().filter(message -> message.email().receivedAt().isBefore(cutoff)).toList();

        AppUser owner = setUpOwner();
        Map<String, MailAccount> accounts = setUpAccounts(owner, all);
        setUpSchema();

        Map<String, Long> emailIdByMessage = new HashMap<>();
        Map<Long, String> messageByEmailId = new HashMap<>();
        List<Long> millis = new ArrayList<>();
        LocalDate week = null;
        long start = System.nanoTime();
        for (BusinessMessage message : replayed) {
            LocalDate day = message.email().receivedAt().atZone(ZONE).toLocalDate();
            if (week != null && !day.with(DayOfWeek.MONDAY).equals(week)) {
                runInsights(owner, week.with(DayOfWeek.FRIDAY));
            }
            week = day.with(DayOfWeek.MONDAY);
            moveClockTo(message.email().receivedAt().plusSeconds(60));
            Long emailId = transactions.execute(status -> store(message, accounts.get(message.account())));
            emailIdByMessage.put(message.id(), emailId);
            messageByEmailId.put(emailId, message.id());
            if (!message.sent()) {
                long begin = System.nanoTime();
                awaitProcessed(emailId);
                millis.add(Duration.ofNanos(System.nanoTime() - begin).toMillis());
            }
        }
        if (week != null) {
            runInsights(owner, week.with(DayOfWeek.FRIDAY));
        }
        Duration total = Duration.ofNanos(System.nanoTime() - start);

        // what the collections hold now
        List<CollectionDto> collections = collectionSchemaService.findAll();
        Map<Long, String> slugById = collections.stream().collect(Collectors.toMap(CollectionDto::getId, CollectionDto::getSlug));
        Set<String> evaluated = EvalCollections.business().stream().map(CollectionDto::getSlug).collect(Collectors.toSet());
        Map<String, List<ProducedRecord>> actual = new TreeMap<>();
        Map<String, String> titleByRecord = new HashMap<>();
        List<RecordDto> allRecords = new ArrayList<>();
        for (CollectionDto collection : collections) {
            if (!evaluated.contains(collection.getSlug())) {
                continue;
            }
            for (RecordDto record : records(collection.getId())) {
                actual.computeIfAbsent(collection.getSlug(), key -> new ArrayList<>())
                        .add(new ProducedRecord(collection.getSlug(), record.getTitle(), record.getValues()));
                titleByRecord.put(collection.getId() + ":" + record.getId(), record.getTitle());
                allRecords.add(record);
            }
        }
        Map<Long, String> relationTypeById = relationTypeService.findAll().stream().collect(Collectors.toMap(RelationTypeDto::getId, RelationTypeDto::getSlug));
        Map<Long, RelationDto> relations = new LinkedHashMap<>();
        for (RecordDto record : allRecords) {
            relationService.listRelations(record.getCollectionId(), record.getId()).forEach(relation -> relations.put(relation.getId(), relation));
        }
        List<ProducedRelation> actualRelations = relations.values().stream().map(relation -> new ProducedRelation(
                relationTypeById.get(relation.getRelationTypeId()), slugById.get(relation.getFromCollectionId()),
                titleByRecord.get(relation.getFromCollectionId() + ":" + relation.getFromRecordId()), slugById.get(relation.getToCollectionId()),
                titleByRecord.get(relation.getToCollectionId() + ":" + relation.getToRecordId()))).toList();

        BusinessCorpus.EndState expected = cut(BusinessCorpus.endState(), replayed);
        EndStateComparison.Result comparison = EndStateComparison.compare(expected, actual, actualRelations, EvalCollections.business());

        // proposals: insights and reply drafts
        List<InsightMatch.Proposed> insights = new ArrayList<>();
        Map<String, Integer> drafts = new HashMap<>();
        transactions.executeWithoutResult(status -> {
            for (Proposal proposal : proposalRepository.findAll()) {
                if (proposal.getKind() == ProposalKind.INSIGHT && proposal.getPayload().get("evidenceEmailIds") instanceof List<?> ids) {
                    insights.add(new InsightMatch.Proposed(ids.stream().map(id -> messageByEmailId.get(((Number) id).longValue())).toList(),
                            proposal.getCreatedAt().atZone(ZONE).toLocalDate()));
                } else if (proposal.getKind() == ProposalKind.DRAFT_REPLY && proposal.getSourceEmail() != null) {
                    drafts.merge(messageByEmailId.get(proposal.getSourceEmail().getId().id()), 1, Integer::sum);
                }
            }
        });

        String report = report(replayed, expected, comparison, insights, drafts, millis, total);
        Path dir = Path.of("target", "ai-eval");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("replay-report.md"), report);
        System.out.println(report);
        assertThat(millis).as("processed mails").isNotEmpty();
    }

    private AppUser setUpOwner() {
        AppUser owner = appUserRepository.save(AppUser.builder().username(new Username("alex")).email(new Email("alex.morgan@nordhafen.example"))
                .passwordHash("x").role(UserRole.OWNER).build());
        var principal = AuthenticatedUser.of(owner);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        aiSettingsRepository.save(AiSettings.builder().user(owner).mailAccess(true).categories(AiPermissionLevel.OFF)
                .records(AiPermissionLevel.AUTO).calendar(AiPermissionLevel.AUTO).tasks(AiPermissionLevel.AUTO)
                .replies(AiPermissionLevel.SUGGEST).courtesyReplies(true).ownerProfile(DemoMailboxService.OWNER_PROFILE)
                .onboardedAt(clock.instant()).build());
        return owner;
    }

    private Map<String, MailAccount> setUpAccounts(AppUser owner, List<BusinessMessage> all) {
        Map<String, MailAccount> accounts = new HashMap<>();
        all.stream().collect(Collectors.toMap(BusinessMessage::account, message -> message.email().mailbox(), (a, b) -> a))
                .forEach((account, address) -> accounts.put(account, mailAccountRepository.save(MailAccount.builder().owner(owner)
                        .provider(MailProvider.MICROSOFT_GRAPH).emailAddress(new Email(address)).displayName(account).build())));
        return accounts;
    }

    /** The business collections and relation types next to the seeded system ones. */
    private void setUpSchema() {
        Set<String> existing = collectionSchemaService.findAll().stream().map(CollectionDto::getSlug).collect(Collectors.toSet());
        EvalCollections.businessCommands().stream().filter(command -> !existing.contains(command.getSlug()))
                .forEach(collectionSchemaService::createCollection);
        Map<String, Long> ids = collectionSchemaService.findAll().stream().collect(Collectors.toMap(CollectionDto::getSlug, CollectionDto::getId));
        Set<String> types = relationTypeService.findAll().stream().map(RelationTypeDto::getSlug).collect(Collectors.toSet());
        RELATION_TYPES.forEach((slug, spec) -> {
            if (!types.contains(slug)) {
                relationTypeService.createRelationType(new NewRelationTypeCommand(slug, spec[0]).inverseName(spec[1])
                        .fromCollectionId(ids.get(spec[2])).toCollectionId(ids.get(spec[3])).directed(true));
            }
        });
    }

    private Long store(BusinessMessage message, MailAccount account) {
        var email = message.email();
        String body = email.body();
        EmailMessage stored = emailMessageRepository.saveAndFlush(EmailMessage.builder().account(account)
                .providerMessageId(message.id()).internetMessageId("<" + message.id() + "@replay.example>")
                .conversationId(message.conversationId()).folder(message.sent() ? EmailMessage.FOLDER_SENT : "inbox")
                .fromName(email.fromName()).fromAddress(email.fromAddress())
                .toRecipients(new ArrayList<>(email.to().stream().map(address -> new EmailRecipient(null, address)).toList()))
                .ccRecipients(new ArrayList<>(message.cc().stream().map(address -> new EmailRecipient(null, address)).toList()))
                .subject(email.subject()).snippet(body.substring(0, Math.min(200, body.length()))).bodyText(body)
                .receivedAt(email.receivedAt()).processingState(message.sent() ? ProcessingState.SKIPPED : ProcessingState.NEW).build());
        return stored.getId().id();
    }

    /** Asks the worker to process new mail until this email is done (the worker runs on its own thread). */
    private void awaitProcessed(Long emailId) {
        Instant deadline = Instant.now().plus(PROCESSING_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            worker.processNewMail();
            ProcessingState state = emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId))
                    .map(EmailMessage::getProcessingState).orElse(ProcessingState.NEW);
            if (state != ProcessingState.NEW && state != ProcessingState.PROCESSING) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Email " + emailId + " was not processed within " + PROCESSING_TIMEOUT);
    }

    private void runInsights(AppUser owner, LocalDate friday) {
        moveClockTo(friday.atTime(LocalTime.of(18, 0)).atZone(ZONE).toInstant());
        insightService.runFor(owner.getId());
    }

    private void moveClockTo(Instant target) {
        if (target.isAfter(clock.instant())) {
            clock.advance(Duration.between(clock.instant(), target));
        }
    }

    private List<RecordDto> records(Long collectionId) {
        List<RecordDto> records = new ArrayList<>();
        for (int page = 0; ; page++) {
            RecordPageDto result = recordService.listRecords(collectionId, List.of(), null, null, page, 100);
            records.addAll(result.getItems());
            if (result.getItems().size() < 100) {
                return records;
            }
        }
    }

    /** The expected state restricted to records whose first source was replayed (all of it for a full replay). */
    private static BusinessCorpus.EndState cut(BusinessCorpus.EndState state, List<BusinessMessage> replayed) {
        Set<String> ids = replayed.stream().map(BusinessMessage::id).collect(Collectors.toSet());
        Map<String, List<BusinessCorpus.StateRecord>> records = new LinkedHashMap<>();
        state.records().forEach((slug, list) -> records.put(slug, list.stream().filter(record -> ids.contains(record.sources().getFirst())).toList()));
        List<BusinessCorpus.StateRelation> relations = state.relations().stream().filter(relation -> ids.contains(relation.sources().getFirst())).toList();
        return new BusinessCorpus.EndState(state.asOf(), records, relations);
    }

    private static String report(List<BusinessMessage> replayed, BusinessCorpus.EndState expected, EndStateComparison.Result comparison,
                                 List<InsightMatch.Proposed> insights, Map<String, Integer> drafts, List<Long> millis, Duration total) {
        StringBuilder md = new StringBuilder("# Mailbox replay\n\n");
        md.append("%d messages replayed (%d processed) in %d min; per mail p50 %d ms, p95 %d ms%n%n".formatted(replayed.size(), millis.size(),
                total.toMinutes(), percentile(millis, 0.5), percentile(millis, 0.95)));
        md.append(comparison.report().toMarkdown().replace("# Extraction eval: replay / end-state", "## End state"));
        md.append("\n- Duplicates (same entity stored more than once): ").append(comparison.duplicates().isEmpty() ? "none" : comparison.duplicates())
                .append('\n');

        Set<String> ids = replayed.stream().map(BusinessMessage::id).collect(Collectors.toSet());
        md.append("\n## Insights\n\n");
        Map<InsightMatch.Status, Integer> statuses = new TreeMap<>();
        for (BusinessCorpus.ExpectedInsight insight : BusinessCorpus.insights()) {
            if (!ids.containsAll(insight.evidence())) {
                continue;
            }
            InsightMatch.Status status = InsightMatch.status(insight, insights);
            statuses.merge(status, 1, Integer::sum);
            md.append("- %s: %s%n".formatted(insight.title(), status));
        }
        md.append("- %s; %d insights proposed in total%n".formatted(statuses, insights.size()));

        int needed = 0;
        int neededWithDraft = 0;
        int answered = 0;
        int answeredWithDraft = 0;
        int notNeededWithDraft = 0;
        int courtesy = 0;
        int courtesyWithDraft = 0;
        for (BusinessMessage message : replayed) {
            if (!message.labelled()) {
                continue;
            }
            boolean draft = drafts.getOrDefault(message.id(), 0) > 0;
            boolean isCourtesy = message.reply() != null && "COURTESY".equals(message.reply().intent());
            boolean isAnswered = message.reply() != null && message.reply().answeredBy() != null;
            if (Boolean.TRUE.equals(message.needsReply()) && isAnswered) {
                answered++;
                answeredWithDraft += draft ? 1 : 0;
            } else if (Boolean.TRUE.equals(message.needsReply())) {
                needed++;
                neededWithDraft += draft ? 1 : 0;
            } else if (isCourtesy) {
                courtesy++;
                courtesyWithDraft += draft ? 1 : 0;
            } else {
                notNeededWithDraft += draft ? 1 : 0;
            }
        }
        md.append("\n## Reply drafts\n\n");
        md.append("- needs a reply, never answered in the mailbox: %d of %d got a draft%n".formatted(neededWithDraft, needed));
        md.append("- needs a reply, answered later by the owner: %d of %d got a draft (drafted before the answer arrived)%n"
                .formatted(answeredWithDraft, answered));
        md.append("- courtesy: %d of %d got a draft%n".formatted(courtesyWithDraft, courtesy));
        md.append("- no reply needed but a draft anyway: %d%n".formatted(notNeededWithDraft));
        md.append("\nExpected records compared: %d%n".formatted(expected.records().values().stream().mapToInt(List::size).sum()));
        return md.toString();
    }

    private static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(Math.max((int) Math.ceil(p * sorted.size()), 1) - 1);
    }
}
