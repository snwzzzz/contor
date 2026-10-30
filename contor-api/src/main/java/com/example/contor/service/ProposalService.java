package com.example.contor.service;

import com.example.contor.ai.ImportanceScorer;
import com.example.contor.ai.ImportanceSignals;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.api.model.DecideProposalCommand;
import com.example.contor.api.model.ProposalCountDto;
import com.example.contor.api.model.ProposalDecision;
import com.example.contor.api.model.ProposalDto;
import com.example.contor.api.model.ProposalGroup;
import com.example.contor.api.model.ProposalPageDto;
import com.example.contor.api.model.ProposalPriority;
import com.example.contor.api.model.ProposalSort;
import com.example.contor.api.model.ProposalState;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalDependency;
import com.example.contor.domain.ProposalImportance;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.domain.Slug;
import com.example.contor.exception.ProposalAlreadyDecidedException;
import com.example.contor.exception.ProposalDependenciesPendingException;
import com.example.contor.exception.ProposalNotApplicableException;
import com.example.contor.exception.ConflictException;
import com.example.contor.exception.ProposalNotFoundException;
import com.example.contor.exception.ResourceNotFoundException;
import com.example.contor.mapper.ProposalMapper;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ProposalDependencyRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.proposal.ProposalApplier;
import com.example.contor.proposal.ProposalApplierRegistry;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Deque;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lists proposals and decides them. Approving runs the kind's {@link ProposalApplier} in the same transaction, so a
 * failing application leaves the proposal PENDING. Every decision is audited.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ProposalService {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final List<String> ACTION_COLLECTIONS = List.of("events", "tasks");

    /** What happened to a submitted draft. */
    public enum Outcome { SUGGESTED, AUTO_APPLIED }

    /** A stored draft: its proposal id (for later proposals to depend on) and what happened to it. */
    public record Submission(Long proposalId, Outcome outcome) {}

    /** A dependency counts as satisfied once it was applied. */
    private static final Set<ProposalStatus> APPLIED = Set.of(ProposalStatus.APPROVED, ProposalStatus.EDITED_AND_APPROVED, ProposalStatus.AUTO_APPLIED);

    private final ProposalRepository proposalRepository;
    private final ProposalDependencyRepository proposalDependencyRepository;
    private final AppUserRepository appUserRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final CollectionRepository collectionRepository;
    private final AuditService auditService;
    private final ProposalApplierRegistry proposalApplierRegistry;
    private final Clock clock;

    /**
     * @param minImportance only proposals of at least this importance ({@code null} = all)
     * @param sort          NEWEST (default) or IMPORTANCE (highest score first, then newest)
     */
    @Transactional
    public ProposalPageDto listProposals(ProposalState status, ProposalGroup group, ProposalPriority minImportance, ProposalSort sort,
                                         Integer page, Integer size) {
        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        int minScore = minScore(minImportance);
        boolean byImportance = sort == ProposalSort.IMPORTANCE;
        Page<Proposal> result;
        if (group != null) {
            // Grouped queries are native SQL with their own order, so the page carries no sort.
            String state = status != null ? status.name() : ProposalStatus.PENDING.name();
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            result = group == ProposalGroup.FINDINGS
                    ? proposalRepository.findFindings(state, actionCollectionIds(), minScore, byImportance, pageable)
                    : proposalRepository.findActions(state, actionCollectionIds(), minScore, byImportance, pageable);
        } else {
            Sort order = byImportance
                    ? Sort.by(Sort.Order.desc("importanceScore"), Sort.Order.desc("createdAt"))
                    : Sort.by(Sort.Direction.DESC, "createdAt");
            Pageable pageable = PageRequest.of(pageNumber, pageSize, order);
            result = status == null
                    ? proposalRepository.findByImportanceScoreGreaterThanEqual(minScore, pageable)
                    : proposalRepository.findByStatusAndImportanceScoreGreaterThanEqual(ProposalStatus.valueOf(status.name()), minScore, pageable);
        }
        return new ProposalPageDto(new ArrayList<>(result.getContent().stream().map(ProposalMapper::toDto).toList()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ProposalCountDto countProposals(ProposalState status, ProposalGroup group, ProposalPriority minImportance) {
        ProposalStatus counted = status != null ? ProposalStatus.valueOf(status.name()) : ProposalStatus.PENDING;
        int minScore = minScore(minImportance);
        if (group == null) {
            return new ProposalCountDto(proposalRepository.countByStatusAndImportanceScoreGreaterThanEqual(counted, minScore));
        }
        return new ProposalCountDto(group == ProposalGroup.FINDINGS
                ? proposalRepository.countFindings(counted.name(), actionCollectionIds(), minScore)
                : proposalRepository.countActions(counted.name(), actionCollectionIds(), minScore));
    }

    private static int minScore(ProposalPriority minImportance) {
        return minImportance != null ? ProposalImportance.valueOf(minImportance.name()).minScore() : 0;
    }

    /**
     * Gives pending proposals that existed before importance did (no reason yet) their score, once, after startup. They
     * are scored from their kind and payload only: the signals of their source email were never stored.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void scoreUnscoredProposals() {
        for (Proposal proposal : proposalRepository.findByStatusAndImportanceReasonIsNull(ProposalStatus.PENDING)) {
            applyImportance(proposal, ImportanceSignals.NONE);
            proposalRepository.saveAndFlush(proposal);
        }
    }

    /** Scores {@code proposal} from its kind, its collection, its payload and what is known about its source email. */
    private void applyImportance(Proposal proposal, ImportanceSignals signals) {
        String slug = proposal.getPayload().get("collectionId") instanceof Number id
                ? collectionRepository.findById(new Collection.CollectionId(id.longValue()))
                        .map(collection -> collection.getSlug().value()).orElse(null)
                : null;
        ImportanceScorer.Importance importance = ImportanceScorer.score(proposal.getKind(), slug, proposal.getPayload(),
                signals != null ? signals : ImportanceSignals.NONE, clock.instant(), clock.getZone());
        proposal.setImportanceScore(importance.score());
        proposal.setImportance(importance.level());
        proposal.setImportanceReason(importance.reason());
    }

    /** The assistant's calendar and to-do collections: records there are actions, not findings. Never empty. */
    private List<Long> actionCollectionIds() {
        List<Long> ids = ACTION_COLLECTIONS.stream()
                .map(slug -> collectionRepository.findBySlug(new Slug(slug)))
                .flatMap(Optional::stream)
                .map(collection -> collection.getId().id())
                .toList();
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    @Transactional
    public ProposalDto getProposal(Long id) {
        return ProposalMapper.toDto(load(id));
    }

    @Transactional
    public ProposalDto decideProposal(Long id, DecideProposalCommand command) {
        Proposal proposal = load(id);
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ProposalAlreadyDecidedException(id);
        }

        boolean rejected = command.getDecision() == ProposalDecision.REJECT;
        AuditAction action;
        if (rejected) {
            if (command.getPayload() != null) {
                throw new IllegalArgumentException("payload is only allowed when approving");
            }
            proposal.setStatus(ProposalStatus.REJECTED);
            proposal.setAutoApplyWhenReady(false);
            action = AuditAction.PROPOSAL_REJECTED;
        } else {
            ProposalApplier applier = proposalApplierRegistry.applier(proposal.getKind())
                    .orElseThrow(() -> new ProposalNotApplicableException(proposal.getKind()));
            if (applier.requiresOwner() && !CurrentActor.isOwner()) {
                throw new AccessDeniedException("Only the owner may approve " + proposal.getKind() + " proposals");
            }
            if (!dependenciesApplied(proposal)) {
                throw new ProposalDependenciesPendingException(id);
            }
            boolean edited = command.getPayload() != null;
            proposal.setFinalPayload(new LinkedHashMap<>(edited ? command.getPayload() : proposal.getPayload()));
            proposal.setResult(resultOf(applier.apply(proposal)));
            proposal.setAutoApplyWhenReady(false);
            proposal.setStatus(edited ? ProposalStatus.EDITED_AND_APPROVED : ProposalStatus.APPROVED);
            action = AuditAction.PROPOSAL_APPROVED;
        }
        proposal.setDecidedAt(Instant.now());
        proposal.setDecidedBy(currentUserReference());
        Proposal saved = proposalRepository.saveAndFlush(proposal);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", saved.getStatus().name());
        after.put("kind", saved.getKind().name());
        if (saved.getFinalPayload() != null) {
            after.put("finalPayload", saved.getFinalPayload());
        }
        auditService.record(AuditEvent.currentActor(action)
                .entity("proposal", saved.getId().id())
                .before(Map.of("status", ProposalStatus.PENDING.name()))
                .after(after));
        if (rejected) {
            rejectDependents(saved);
        } else {
            applyReadyDependents(saved);
        }
        return ProposalMapper.toDto(saved);
    }

    /**
     * Stores an AI draft: SUGGEST keeps it PENDING for review, AUTO applies it right away (AUTO_APPLIED, audited as
     * the AI). A failing application throws and rolls back, so the caller can resubmit it as a suggestion.
     */
    @Transactional
    public Outcome submitDraft(ProposalDraft draft, Long sourceEmailId, AiPermissionLevel level, String model, String promptVersion) {
        return submitDraft(draft, sourceEmailId, level, model, promptVersion, List.of()).outcome();
    }

    /**
     * Like {@link #submitDraft(ProposalDraft, Long, AiPermissionLevel, String, String)} for a draft that needs other
     * proposals (e.g. a relation needs its two records): it can only be decided once they are applied. AUTO with
     * unmet dependencies stays PENDING and is applied automatically as soon as they are applied.
     *
     * @throws IllegalArgumentException a dependency was rejected, failed or undone (it can never be applied)
     */
    @Transactional
    public Submission submitDraft(ProposalDraft draft, Long sourceEmailId, AiPermissionLevel level, String model,
                                  String promptVersion, java.util.Collection<Long> dependsOn) {
        EmailMessage sourceEmail = sourceEmailId != null
                ? emailMessageRepository.getReferenceById(new EmailMessage.EmailMessageId(sourceEmailId))
                : null;
        return submit(draft, sourceEmail, null, level, model, promptVersion, dependsOn);
    }

    /**
     * A draft made from an item of an external API (no model involved: a confirmed mapping turned the item into a
     * record): SUGGEST keeps it PENDING, AUTO applies it. The appliers link the record to {@code item}.
     */
    @Transactional
    public Outcome submitDraftFromApiItem(ProposalDraft draft, ExternalApiItem item, AiPermissionLevel level) {
        return submit(draft, null, item, level, null, null, List.of()).outcome();
    }

    private Submission submit(ProposalDraft draft, EmailMessage sourceEmail, ExternalApiItem sourceApiItem, AiPermissionLevel level,
                              String model, String promptVersion, java.util.Collection<Long> dependsOn) {
        if (!level.allowed()) {
            throw new IllegalArgumentException("The AI may not create " + draft.kind() + " proposals here");
        }
        List<Proposal> dependencies = dependsOn.stream().distinct().map(this::load).toList();
        for (Proposal dependency : dependencies) {
            if (dependency.getStatus() != ProposalStatus.PENDING && !APPLIED.contains(dependency.getStatus())) {
                throw new IllegalArgumentException("Proposal " + dependency.getId().id() + " cannot be a dependency: it is "
                        + dependency.getStatus());
            }
        }
        boolean ready = dependencies.stream().allMatch(dependency -> APPLIED.contains(dependency.getStatus()));
        Proposal proposal = Proposal.builder()
                .kind(draft.kind())
                .payload(new LinkedHashMap<>(draft.payload()))
                .confidence(draft.confidence())
                .reasoning(draft.reasoning())
                .model(model)
                .promptVersion(promptVersion)
                .sourceEmail(sourceEmail)
                .sourceApiItem(sourceApiItem)
                .build();
        applyImportance(proposal, draft.signals());
        if (level != AiPermissionLevel.AUTO || !ready) {
            proposal.setAutoApplyWhenReady(level == AiPermissionLevel.AUTO);
            Proposal saved = proposalRepository.saveAndFlush(proposal);
            dependencies.forEach(dependency -> addDependency(idOf(saved), dependency.getId().id()));
            return new Submission(idOf(saved), Outcome.SUGGESTED);
        }

        ProposalApplier applier = proposalApplierRegistry.applier(proposal.getKind())
                .orElseThrow(() -> new ProposalNotApplicableException(proposal.getKind()));
        proposal.setFinalPayload(new LinkedHashMap<>(proposal.getPayload()));
        proposal.setResult(resultOf(applier.apply(proposal)));
        proposal.setStatus(ProposalStatus.AUTO_APPLIED);
        proposal.setDecidedAt(Instant.now());
        Proposal saved = proposalRepository.saveAndFlush(proposal);
        dependencies.forEach(dependency -> addDependency(idOf(saved), dependency.getId().id()));
        auditService.record(AuditEvent.ai(AuditAction.PROPOSAL_AUTO_APPLIED)
                .entity("proposal", saved.getId() != null ? saved.getId().id() : null)
                .after(Map.of("kind", saved.getKind().name(), "finalPayload", saved.getFinalPayload())));
        return new Submission(idOf(saved), Outcome.AUTO_APPLIED);
    }

    /**
     * Makes {@code proposalId} depend on {@code dependsOnId} (idempotent).
     *
     * @throws IllegalArgumentException a proposal cannot depend on itself, and the edge may not close a cycle
     */
    @Transactional
    public void addDependency(Long proposalId, Long dependsOnId) {
        if (proposalId.equals(dependsOnId)) {
            throw new IllegalArgumentException("A proposal cannot depend on itself");
        }
        load(proposalId);
        load(dependsOnId);
        if (proposalDependencyRepository.findById_ProposalId(proposalId).stream()
                .anyMatch(edge -> edge.getId().dependsOnProposalId().equals(dependsOnId))) {
            return;
        }
        if (dependsTransitively(dependsOnId, proposalId)) {
            throw new IllegalArgumentException("Proposal " + proposalId + " cannot depend on " + dependsOnId + ": that would be a cycle");
        }
        proposalDependencyRepository.save(new ProposalDependency(new ProposalDependency.ProposalDependencyId(proposalId, dependsOnId)));
    }

    /** Whether {@code start} (transitively) depends on {@code target}. */
    private boolean dependsTransitively(Long start, Long target) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> open = new ArrayDeque<>(List.of(start));
        while (!open.isEmpty()) {
            Long current = open.pop();
            if (!seen.add(current)) {
                continue;
            }
            for (ProposalDependency edge : proposalDependencyRepository.findById_ProposalId(current)) {
                Long next = edge.getId().dependsOnProposalId();
                if (next.equals(target)) {
                    return true;
                }
                open.push(next);
            }
        }
        return false;
    }

    private boolean dependenciesApplied(Proposal proposal) {
        return proposalDependencyRepository.findById_ProposalId(proposal.getId().id()).stream()
                .allMatch(edge -> APPLIED.contains(load(edge.getId().dependsOnProposalId()).getStatus()));
    }

    /** Rejecting a proposal rejects everything pending that (transitively) needs it. */
    private void rejectDependents(Proposal rejected) {
        for (ProposalDependency edge : proposalDependencyRepository.findById_DependsOnProposalId(rejected.getId().id())) {
            Proposal dependent = load(edge.getId().proposalId());
            if (dependent.getStatus() != ProposalStatus.PENDING) {
                continue;
            }
            dependent.setStatus(ProposalStatus.REJECTED);
            dependent.setAutoApplyWhenReady(false);
            dependent.setDecidedAt(Instant.now());
            dependent.setDecidedBy(currentUserReference());
            proposalRepository.saveAndFlush(dependent);
            auditService.record(AuditEvent.currentActor(AuditAction.PROPOSAL_CASCADE_REJECTED)
                    .entity("proposal", dependent.getId().id())
                    .before(Map.of("status", ProposalStatus.PENDING.name()))
                    .after(Map.of("status", ProposalStatus.REJECTED.name(), "kind", dependent.getKind().name(),
                            "rejectedDependency", rejected.getId().id())));
            rejectDependents(dependent);
        }
    }

    /**
     * Applies the dependents that were waiting for AUTO (all their dependencies are applied now), and continues
     * with theirs. A dependent that cannot be applied stays PENDING for review; the decision that triggered this is
     * not affected.
     */
    private void applyReadyDependents(Proposal applied) {
        for (ProposalDependency edge : proposalDependencyRepository.findById_DependsOnProposalId(applied.getId().id())) {
            Proposal dependent = load(edge.getId().proposalId());
            if (dependent.getStatus() != ProposalStatus.PENDING || !dependent.isAutoApplyWhenReady() || !dependenciesApplied(dependent)) {
                continue;
            }
            if (autoApply(dependent)) {
                applyReadyDependents(dependent);
            }
        }
    }

    private boolean autoApply(Proposal proposal) {
        try {
            ProposalApplier applier = proposalApplierRegistry.applier(proposal.getKind())
                    .orElseThrow(() -> new ProposalNotApplicableException(proposal.getKind()));
            proposal.setFinalPayload(new LinkedHashMap<>(proposal.getPayload()));
            proposal.setResult(resultOf(applier.apply(proposal)));
        } catch (IllegalArgumentException | ResourceNotFoundException | ConflictException e) {
            log.info("Proposal {} could not be applied automatically and waits for review: {}", proposal.getId().id(), e.getMessage());
            proposal.setFinalPayload(null);
            proposal.setAutoApplyWhenReady(false);
            proposalRepository.saveAndFlush(proposal);
            return false;
        }
        proposal.setStatus(ProposalStatus.AUTO_APPLIED);
        proposal.setAutoApplyWhenReady(false);
        proposal.setDecidedAt(Instant.now());
        Proposal saved = proposalRepository.saveAndFlush(proposal);
        auditService.record(AuditEvent.ai(AuditAction.PROPOSAL_AUTO_APPLIED)
                .entity("proposal", saved.getId().id())
                .after(Map.of("kind", saved.getKind().name(), "finalPayload", saved.getFinalPayload())));
        return true;
    }

    private static Long idOf(Proposal proposal) {
        return proposal.getId() != null ? proposal.getId().id() : null;
    }

    private static Map<String, Object> resultOf(Map<String, Object> result) {
        return result != null ? new LinkedHashMap<>(result) : new LinkedHashMap<>();
    }

    private AppUser currentUserReference() {
        return CurrentActor.userId()
                .map(userId -> appUserRepository.getReferenceById(new AppUser.AppUserId(userId)))
                .orElse(null);
    }

    private Proposal load(Long id) {
        return proposalRepository.findById(new Proposal.ProposalId(id))
                .orElseThrow(() -> new ProposalNotFoundException(id));
    }
}
