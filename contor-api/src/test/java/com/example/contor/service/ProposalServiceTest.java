package com.example.contor.service;

import com.example.contor.api.model.DecideProposalCommand;
import com.example.contor.api.model.ProposalDecision;
import com.example.contor.api.model.ProposalDto;
import com.example.contor.api.model.ProposalGroup;
import com.example.contor.api.model.ProposalPageDto;
import com.example.contor.api.model.ProposalPriority;
import com.example.contor.api.model.ProposalSort;
import com.example.contor.api.model.ProposalState;
import com.example.contor.ai.ImportanceSignals;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.domain.ActorType;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.Slug;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalImportance;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.ProposalAlreadyDecidedException;
import com.example.contor.exception.ProposalDependenciesPendingException;
import com.example.contor.domain.ProposalDependency;
import com.example.contor.persistence.ProposalDependencyRepository;
import com.example.contor.exception.ProposalNotApplicableException;
import com.example.contor.exception.ProposalNotFoundException;
import com.example.contor.exception.RecordValidationException;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.proposal.ProposalApplier;
import com.example.contor.proposal.ProposalApplierRegistry;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private ProposalDependencyRepository dependencyRepository;

    @Mock
    private ProposalApplier createRecordApplier;

    private ProposalService proposalService;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        lenient().when(createRecordApplier.kind()).thenReturn(ProposalKind.CREATE_RECORD);
        proposalService = new ProposalService(proposalRepository, dependencyRepository, appUserRepository, emailMessageRepository, collectionRepository, auditService,
                new ProposalApplierRegistry(List.of(createRecordApplier)), CLOCK);
        lenient().when(proposalRepository.saveAndFlush(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        loggedInAs(UserRole.OWNER);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private static void loggedInAs(UserRole role) {
        var principal = new AuthenticatedUser(1L, "owner", role);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private Proposal pending(ProposalKind kind) {
        var proposal = Proposal.builder().id(new Proposal.ProposalId(5L)).kind(kind).status(ProposalStatus.PENDING)
                .payload(Map.of("collectionId", 1, "values", Map.of("tracking", "T-1"))).build();
        when(proposalRepository.findById(new Proposal.ProposalId(5L))).thenReturn(Optional.of(proposal));
        return proposal;
    }

    @Test
    void decideProposal_ApproveAppliesTheProposedPayload() {
        Proposal proposal = pending(ProposalKind.CREATE_RECORD);
        when(appUserRepository.getReferenceById(new AppUser.AppUserId(1L))).thenReturn(AppUser.builder().id(new AppUser.AppUserId(1L)).build());

        ProposalDto result = proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE));

        assertEquals(ProposalState.APPROVED, result.getStatus());
        assertEquals(proposal.getPayload(), result.getFinalPayload());
        assertNotNull(result.getDecidedAt());
        assertEquals(1L, result.getDecidedById());
        verify(createRecordApplier).apply(argThat(p -> p.getFinalPayload().equals(proposal.getPayload())));
        verify(auditService).record(argThat(e -> e.action() == AuditAction.PROPOSAL_APPROVED && e.entityId() == 5L));
    }

    @Test
    void decideProposal_ApproveWithEditedPayload() {
        pending(ProposalKind.CREATE_RECORD);
        Map<String, Object> edited = Map.of("collectionId", 1, "values", Map.of("tracking", "T-2"));

        ProposalDto result = proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE).payload(edited));

        assertEquals(ProposalState.EDITED_AND_APPROVED, result.getStatus());
        assertEquals(edited, result.getFinalPayload());
        verify(createRecordApplier).apply(argThat(p -> p.getFinalPayload().equals(edited)));
    }

    @Test
    void decideProposal_Reject() {
        pending(ProposalKind.CREATE_RECORD);

        ProposalDto result = proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.REJECT));

        assertEquals(ProposalState.REJECTED, result.getStatus());
        assertNull(result.getFinalPayload());
        verify(createRecordApplier, never()).apply(any());
        verify(auditService).record(argThat(e -> e.action() == AuditAction.PROPOSAL_REJECTED));
    }

    @Test
    void decideProposal_RejectWithPayloadRejected() {
        pending(ProposalKind.CREATE_RECORD);

        assertThrows(IllegalArgumentException.class,
                () -> proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.REJECT).payload(Map.of())));
        verify(proposalRepository, never()).saveAndFlush(any());
    }

    @Test
    void decideProposal_AlreadyDecided() {
        pending(ProposalKind.CREATE_RECORD).setStatus(ProposalStatus.REJECTED);

        assertThrows(ProposalAlreadyDecidedException.class,
                () -> proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE)));
        verify(createRecordApplier, never()).apply(any());
    }

    @Test
    void decideProposal_KindWithoutApplier() {
        Proposal proposal = pending(ProposalKind.CREATE_COLLECTION);

        var exception = assertThrows(ProposalNotApplicableException.class,
                () -> proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE)));
        assertEquals("Proposals of kind CREATE_COLLECTION cannot be applied yet", exception.getMessage());
        assertEquals(ProposalStatus.PENDING, proposal.getStatus());
    }

    @Test
    void decideProposal_FailedApplicationKeepsProposalPending() {
        Proposal proposal = pending(ProposalKind.CREATE_RECORD);
        doThrow(new RecordValidationException(List.of("tracking: is required"))).when(createRecordApplier).apply(any());

        assertThrows(RecordValidationException.class,
                () -> proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE)));
        assertEquals(ProposalStatus.PENDING, proposal.getStatus());
        verify(proposalRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void decideProposal_OwnerOnlyKindsNeedTheOwner() {
        pending(ProposalKind.CREATE_RECORD);
        when(createRecordApplier.requiresOwner()).thenReturn(true);
        loggedInAs(UserRole.USER);

        assertThrows(AccessDeniedException.class,
                () -> proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE)));
        verify(createRecordApplier, never()).apply(any());
    }

    @Test
    void listProposals_FilteredByStatusNewestFirst() {
        Pageable expected = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(proposalRepository.findByStatusAndImportanceScoreGreaterThanEqual(ProposalStatus.PENDING, 0, expected))
                .thenReturn(new PageImpl<>(List.of(Proposal.builder().kind(ProposalKind.SET_LABEL).payload(Map.of()).build()), expected, 11));

        ProposalPageDto page = proposalService.listProposals(ProposalState.PENDING, null, null, null, 1, 10);

        assertEquals(1, page.getItems().size());
        assertEquals(11L, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void listProposals_Unfiltered() {
        Pageable expected = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(proposalRepository.findByImportanceScoreGreaterThanEqual(0, expected)).thenReturn(new PageImpl<>(List.of(), expected, 0));

        assertEquals(0, proposalService.listProposals(null, null, null, null, null, null).getTotalElements());
    }

    private void actionCollections() {
        when(collectionRepository.findBySlug(new Slug("events")))
                .thenReturn(Optional.of(Collection.builder().id(new Collection.CollectionId(3L)).build()));
        when(collectionRepository.findBySlug(new Slug("tasks")))
                .thenReturn(Optional.of(Collection.builder().id(new Collection.CollectionId(4L)).build()));
    }

    @Test
    void listProposals_FindingsExcludeTheCalendarAndToDos() {
        actionCollections();
        Pageable expected = PageRequest.of(0, 25);
        when(proposalRepository.findFindings(ProposalStatus.PENDING.name(), List.of(3L, 4L), 0, false, expected))
                .thenReturn(new PageImpl<>(List.of(Proposal.builder().kind(ProposalKind.CREATE_RECORD).payload(Map.of()).build()), expected, 1));

        assertEquals(1, proposalService.listProposals(ProposalState.PENDING, ProposalGroup.FINDINGS, null, null, null, null).getItems().size());
    }

    @Test
    void listProposals_Actions() {
        actionCollections();
        Pageable expected = PageRequest.of(2, 10);
        when(proposalRepository.findActions(ProposalStatus.PENDING.name(), List.of(3L, 4L), 0, false, expected))
                .thenReturn(new PageImpl<>(List.of(), expected, 20));

        assertEquals(20L, proposalService.listProposals(ProposalState.PENDING, ProposalGroup.ACTIONS, null, null, 2, 10).getTotalElements());
    }

    @Test
    void countProposals_ByGroupDefaultsToPending() {
        actionCollections();
        when(proposalRepository.countActions(ProposalStatus.PENDING.name(), List.of(3L, 4L), 0)).thenReturn(5L);
        when(proposalRepository.countFindings(ProposalStatus.PENDING.name(), List.of(3L, 4L), 0)).thenReturn(7L);

        assertEquals(5L, proposalService.countProposals(null, ProposalGroup.ACTIONS, null).getCount());
        assertEquals(7L, proposalService.countProposals(null, ProposalGroup.FINDINGS, null).getCount());
    }

    @Test
    void listProposals_GroupWithoutActionCollectionsStillWorks() {
        when(collectionRepository.findBySlug(any())).thenReturn(Optional.empty());
        Pageable expected = PageRequest.of(0, 25);
        when(proposalRepository.findFindings(ProposalStatus.PENDING.name(), List.of(-1L), 0, false, expected))
                .thenReturn(new PageImpl<>(List.of(), expected, 0));

        assertEquals(0L, proposalService.listProposals(null, ProposalGroup.FINDINGS, null, null, null, null).getTotalElements());
    }

    @Test
    void listProposals_AtLeastHighMostImportantFirst() {
        Pageable expected = PageRequest.of(0, 25, Sort.by(Sort.Order.desc("importanceScore"), Sort.Order.desc("createdAt")));
        when(proposalRepository.findByStatusAndImportanceScoreGreaterThanEqual(ProposalStatus.PENDING, 60, expected))
                .thenReturn(new PageImpl<>(List.of(), expected, 4));

        assertEquals(4L, proposalService.listProposals(ProposalState.PENDING, null, ProposalPriority.HIGH, ProposalSort.IMPORTANCE, null, null)
                .getTotalElements());
    }

    @Test
    void listProposals_GroupFilteredAndSortedByImportance() {
        actionCollections();
        Pageable expected = PageRequest.of(0, 25);
        when(proposalRepository.findActions(ProposalStatus.PENDING.name(), List.of(3L, 4L), 80, true, expected))
                .thenReturn(new PageImpl<>(List.of(), expected, 2));

        assertEquals(2L, proposalService.listProposals(null, ProposalGroup.ACTIONS, ProposalPriority.URGENT, ProposalSort.IMPORTANCE, null, null)
                .getTotalElements());
    }

    @Test
    void countProposals_WithAMinimumImportance() {
        actionCollections();
        when(proposalRepository.countByStatusAndImportanceScoreGreaterThanEqual(ProposalStatus.PENDING, 35)).thenReturn(6L);
        when(proposalRepository.countActions(ProposalStatus.PENDING.name(), List.of(3L, 4L), 60)).thenReturn(2L);

        assertEquals(6L, proposalService.countProposals(null, null, ProposalPriority.NORMAL).getCount());
        assertEquals(2L, proposalService.countProposals(null, ProposalGroup.ACTIONS, ProposalPriority.HIGH).getCount());
    }

    @Test
    void submitDraft_ScoresTheProposalFromItsCollectionPayloadAndSignals() {
        when(collectionRepository.findById(new Collection.CollectionId(4L)))
                .thenReturn(Optional.of(Collection.builder().id(new Collection.CollectionId(4L)).slug(new Slug("tasks")).build()));
        ProposalDraft task = new ProposalDraft(ProposalKind.CREATE_RECORD,
                Map.of("collectionId", 4, "title", "Pay invoice", "values", Map.of("due_date", "2026-10-13")), new BigDecimal("0.9"), "r")
                .withSignals(new ImportanceSignals(null, true, false, false));

        proposalService.submitDraft(task, null, AiPermissionLevel.SUGGEST, "m", "v");

        // a to-do (50), due tomorrow (+25), from a contact (+10)
        verify(proposalRepository).saveAndFlush(argThat(p -> p.getImportanceScore() == 85 && p.getImportance() == ProposalImportance.URGENT
                && p.getImportanceReason().contains("due within 2 days")));
    }

    @Test
    void submitDraft_WithoutSignalsScoresByKind() {
        proposalService.submitDraft(new ProposalDraft(ProposalKind.DRAFT_REPLY, Map.of(), null, "r"), null, AiPermissionLevel.SUGGEST, "m", "v");

        verify(proposalRepository).saveAndFlush(argThat(p -> p.getImportanceScore() == 55 && p.getImportance() == ProposalImportance.NORMAL));
    }

    @Test
    void scoreUnscoredProposals_ScoresPendingProposalsThatHaveNoReasonYet() {
        Proposal old = Proposal.builder().id(new Proposal.ProposalId(5L)).kind(ProposalKind.DRAFT_REPLY).payload(Map.of()).build();
        when(proposalRepository.findByStatusAndImportanceReasonIsNull(ProposalStatus.PENDING)).thenReturn(List.of(old));

        proposalService.scoreUnscoredProposals();

        assertEquals(55, old.getImportanceScore());
        assertNotNull(old.getImportanceReason());
        verify(proposalRepository).saveAndFlush(old);
    }

    @Test
    void countProposals_DefaultsToPending() {
        when(proposalRepository.countByStatusAndImportanceScoreGreaterThanEqual(ProposalStatus.PENDING, 0)).thenReturn(3L);

        assertEquals(3L, proposalService.countProposals(null, null, null).getCount());
    }

    @Test
    void getProposal_NotFound() {
        when(proposalRepository.findById(new Proposal.ProposalId(9L))).thenReturn(Optional.empty());

        assertThrows(ProposalNotFoundException.class, () -> proposalService.getProposal(9L));
    }

    private static ProposalDraft draft() {
        return new ProposalDraft(ProposalKind.CREATE_RECORD, Map.of("collectionId", 3, "title", "Dentist"),
                new BigDecimal("0.9"), "Appointment confirmation");
    }

    @Test
    void submitDraftFromApiItem_StoresTheItemAsTheSource() {
        var item = ExternalApiItem.builder().id(9L).itemKey("SO-1").build();

        var outcome = proposalService.submitDraftFromApiItem(draft(), item, AiPermissionLevel.SUGGEST);

        assertEquals(ProposalService.Outcome.SUGGESTED, outcome);
        verify(proposalRepository).saveAndFlush(argThat(p -> p.getStatus() == ProposalStatus.PENDING && p.getSourceApiItem() == item
                && p.getSourceEmail() == null && p.getPromptVersion() == null));
        verify(createRecordApplier, never()).apply(any());
    }

    @Test
    void submitDraftFromApiItem_AutoAppliesWithTheItemAsTheSource() {
        var item = ExternalApiItem.builder().id(9L).itemKey("SO-1").build();
        when(createRecordApplier.apply(argThat(p -> p.getSourceApiItem() == item))).thenReturn(Map.of("collectionId", 1L, "recordId", 5L));

        var outcome = proposalService.submitDraftFromApiItem(draft(), item, AiPermissionLevel.AUTO);

        assertEquals(ProposalService.Outcome.AUTO_APPLIED, outcome);
    }

    @Test
    void submitDraft_SuggestStoresPendingProposal() {
        when(emailMessageRepository.getReferenceById(new EmailMessage.EmailMessageId(8L)))
                .thenReturn(EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).build());

        ProposalService.Outcome outcome = proposalService.submitDraft(draft(), 8L, AiPermissionLevel.SUGGEST, "qwen3.5:9b", "extract-v1");

        assertEquals(ProposalService.Outcome.SUGGESTED, outcome);
        verify(proposalRepository).saveAndFlush(argThat(p -> p.getStatus() == ProposalStatus.PENDING
                && p.getSourceEmail().getId().id() == 8L && p.getModel().equals("qwen3.5:9b")
                && p.getPromptVersion().equals("extract-v1") && p.getReasoning().equals("Appointment confirmation")
                && p.getFinalPayload() == null));
        verify(createRecordApplier, never()).apply(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void submitDraft_AutoAppliesAndAudits() {
        ProposalService.Outcome outcome = proposalService.submitDraft(draft(), null, AiPermissionLevel.AUTO, "m", "v");

        assertEquals(ProposalService.Outcome.AUTO_APPLIED, outcome);
        verify(createRecordApplier).apply(argThat(p -> p.getFinalPayload().equals(p.getPayload())));
        verify(proposalRepository).saveAndFlush(argThat(p -> p.getStatus() == ProposalStatus.AUTO_APPLIED && p.getDecidedAt() != null));
        verify(auditService).record(argThat(event -> event.action() == AuditAction.PROPOSAL_AUTO_APPLIED
                && event.actorType() == ActorType.AI));
    }

    @Test
    void submitDraft_AutoWithoutApplierRejected() {
        ProposalDraft draft = new ProposalDraft(ProposalKind.SET_LABEL, Map.of(), null, null);

        assertThrows(ProposalNotApplicableException.class,
                () -> proposalService.submitDraft(draft, null, AiPermissionLevel.AUTO, "m", "v"));
        verify(proposalRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitDraft_OffRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> proposalService.submitDraft(draft(), null, AiPermissionLevel.OFF, "m", "v"));
        verify(proposalRepository, never()).saveAndFlush(any());
    }

    // ---- dependencies and results -----------------------------------------------------------------------------

    private final Map<Long, Proposal> store = new java.util.HashMap<>();
    private final List<long[]> edges = new java.util.ArrayList<>();
    private long nextId = 100;

    /** Wires an in-memory proposal store and dependency edges behind the mocked repositories. */
    private void graph() {
        lenient().when(proposalRepository.findById(any(Proposal.ProposalId.class)))
                .thenAnswer(invocation -> Optional.ofNullable(store.get(((Proposal.ProposalId) invocation.getArgument(0)).id())));
        lenient().when(proposalRepository.saveAndFlush(any(Proposal.class))).thenAnswer(invocation -> {
            Proposal saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(new Proposal.ProposalId(nextId++));
            }
            store.put(saved.getId().id(), saved);
            return saved;
        });
        lenient().when(dependencyRepository.findById_ProposalId(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation -> edges.stream()
                .filter(edge -> edge[0] == (Long) invocation.getArgument(0)).map(ProposalServiceTest::dependency).toList());
        lenient().when(dependencyRepository.findById_DependsOnProposalId(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation -> edges.stream()
                .filter(edge -> edge[1] == (Long) invocation.getArgument(0)).map(ProposalServiceTest::dependency).toList());
        lenient().when(dependencyRepository.save(any(ProposalDependency.class))).thenAnswer(invocation -> {
            ProposalDependency saved = invocation.getArgument(0);
            edges.add(new long[] {saved.getId().proposalId(), saved.getId().dependsOnProposalId()});
            return saved;
        });
    }

    private static ProposalDependency dependency(long[] edge) {
        return new ProposalDependency(new ProposalDependency.ProposalDependencyId(edge[0], edge[1]));
    }

    private Proposal stored(long id, ProposalStatus status) {
        Proposal proposal = Proposal.builder().id(new Proposal.ProposalId(id)).kind(ProposalKind.CREATE_RECORD).status(status)
                .payload(Map.of("collectionId", 1, "title", "P" + id)).build();
        store.put(id, proposal);
        return proposal;
    }

    private void dependsOn(long proposal, long dependency) {
        edges.add(new long[] {proposal, dependency});
    }

    private static ProposalDraft recordDraft() {
        return new ProposalDraft(ProposalKind.CREATE_RECORD, Map.of("collectionId", 1, "title", "Anna"), BigDecimal.ONE, "r");
    }

    @Test
    void submitDraft_AppliedResultIsStored() {
        graph();
        when(createRecordApplier.apply(any())).thenReturn(Map.of("collectionId", 1L, "recordId", 9L));

        var submission = proposalService.submitDraft(recordDraft(), null, AiPermissionLevel.AUTO, "m", "v1", List.of());

        assertEquals(ProposalService.Outcome.AUTO_APPLIED, submission.outcome());
        assertEquals(Map.of("collectionId", 1L, "recordId", 9L), store.get(submission.proposalId()).getResult());
    }

    @Test
    void submitDraft_SuggestedWithDependenciesStaysPendingAndStoresTheEdges() {
        graph();
        stored(5, ProposalStatus.PENDING);
        stored(6, ProposalStatus.APPROVED);

        var submission = proposalService.submitDraft(recordDraft(), null, AiPermissionLevel.SUGGEST, "m", "v1", List.of(5L, 6L));

        assertEquals(ProposalService.Outcome.SUGGESTED, submission.outcome());
        assertEquals(ProposalStatus.PENDING, store.get(submission.proposalId()).getStatus());
        assertEquals(2, edges.stream().filter(edge -> edge[0] == submission.proposalId()).count());
        verify(createRecordApplier, never()).apply(any());
    }

    @Test
    void submitDraft_AutoWithAllDependenciesAppliedAppliesImmediately() {
        graph();
        stored(5, ProposalStatus.AUTO_APPLIED);
        stored(6, ProposalStatus.EDITED_AND_APPROVED);

        var submission = proposalService.submitDraft(recordDraft(), null, AiPermissionLevel.AUTO, "m", "v1", List.of(5L, 6L));

        assertEquals(ProposalService.Outcome.AUTO_APPLIED, submission.outcome());
        assertEquals(ProposalStatus.AUTO_APPLIED, store.get(submission.proposalId()).getStatus());
        verify(createRecordApplier).apply(any());
    }

    @Test
    void submitDraft_AutoWithPendingDependencyWaitsAndRemembersToAutoApply() {
        graph();
        stored(5, ProposalStatus.PENDING);

        var submission = proposalService.submitDraft(recordDraft(), null, AiPermissionLevel.AUTO, "m", "v1", List.of(5L));

        assertEquals(ProposalService.Outcome.SUGGESTED, submission.outcome());
        Proposal waiting = store.get(submission.proposalId());
        assertEquals(ProposalStatus.PENDING, waiting.getStatus());
        assertTrue(waiting.isAutoApplyWhenReady());
        verify(createRecordApplier, never()).apply(any());
    }

    @Test
    void submitDraft_DependencyOnADeadProposalIsRejected() {
        graph();
        stored(5, ProposalStatus.REJECTED);

        assertThrows(IllegalArgumentException.class,
                () -> proposalService.submitDraft(recordDraft(), null, AiPermissionLevel.SUGGEST, "m", "v1", List.of(5L)));
        verify(proposalRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitDraft_UnknownDependencyIsNotFound() {
        graph();

        assertThrows(ProposalNotFoundException.class,
                () -> proposalService.submitDraft(recordDraft(), null, AiPermissionLevel.SUGGEST, "m", "v1", List.of(99L)));
        verify(proposalRepository, never()).saveAndFlush(any());
    }

    @Test
    void decideProposal_ApproveStoresTheApplierResult() {
        graph();
        stored(5, ProposalStatus.PENDING);
        when(createRecordApplier.apply(any())).thenReturn(Map.of("recordId", 9L));

        proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE));

        assertEquals(Map.of("recordId", 9L), store.get(5L).getResult());
    }

    @Test
    void decideProposal_ApproveIsBlockedWhileDependenciesAreNotApplied() {
        graph();
        stored(5, ProposalStatus.PENDING);
        stored(6, ProposalStatus.PENDING);
        dependsOn(6, 5);

        assertThrows(ProposalDependenciesPendingException.class,
                () -> proposalService.decideProposal(6L, new DecideProposalCommand(ProposalDecision.APPROVE)));
        verify(createRecordApplier, never()).apply(any());
        assertEquals(ProposalStatus.PENDING, store.get(6L).getStatus());
    }

    @Test
    void decideProposal_ApproveIsAllowedOnceDependenciesAreApplied() {
        graph();
        stored(5, ProposalStatus.APPROVED);
        stored(6, ProposalStatus.PENDING);
        dependsOn(6, 5);

        ProposalDto result = proposalService.decideProposal(6L, new DecideProposalCommand(ProposalDecision.APPROVE));

        assertEquals(ProposalState.APPROVED, result.getStatus());
    }

    @Test
    void decideProposal_RejectingAnUnblockedDependentIsAlwaysAllowed() {
        graph();
        stored(5, ProposalStatus.PENDING);
        stored(6, ProposalStatus.PENDING);
        dependsOn(6, 5);

        ProposalDto result = proposalService.decideProposal(6L, new DecideProposalCommand(ProposalDecision.REJECT));

        assertEquals(ProposalState.REJECTED, result.getStatus());
        assertEquals(ProposalStatus.PENDING, store.get(5L).getStatus());
    }

    @Test
    void decideProposal_RejectCascadesToPendingDependentsTransitively() {
        graph();
        stored(5, ProposalStatus.PENDING);
        stored(6, ProposalStatus.PENDING);
        stored(7, ProposalStatus.PENDING);
        stored(8, ProposalStatus.APPROVED);
        stored(9, ProposalStatus.PENDING);
        dependsOn(6, 5);
        dependsOn(7, 6);
        dependsOn(8, 5);

        proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.REJECT));

        assertEquals(ProposalStatus.REJECTED, store.get(6L).getStatus());
        assertEquals(ProposalStatus.REJECTED, store.get(7L).getStatus());
        assertEquals(ProposalStatus.APPROVED, store.get(8L).getStatus());
        assertEquals(ProposalStatus.PENDING, store.get(9L).getStatus());
        assertNotNull(store.get(6L).getDecidedAt());
        verify(auditService, times(2)).record(argThat(e -> e.action() == AuditAction.PROPOSAL_CASCADE_REJECTED));
        verify(auditService).record(argThat(e -> e.action() == AuditAction.PROPOSAL_CASCADE_REJECTED && e.entityId() == 7L));
        verify(auditService).record(argThat(e -> e.action() == AuditAction.PROPOSAL_REJECTED && e.entityId() == 5L));
    }

    @Test
    void decideProposal_ApproveAutoAppliesDependentsThatAreReady() {
        graph();
        stored(5, ProposalStatus.PENDING);
        Proposal ready = stored(6, ProposalStatus.PENDING);
        ready.setAutoApplyWhenReady(true);
        Proposal blocked = stored(7, ProposalStatus.PENDING);
        blocked.setAutoApplyWhenReady(true);
        stored(9, ProposalStatus.PENDING);
        Proposal manual = stored(8, ProposalStatus.PENDING);
        dependsOn(6, 5);
        dependsOn(7, 5);
        dependsOn(7, 9);
        dependsOn(8, 5);
        when(createRecordApplier.apply(any())).thenReturn(Map.of("recordId", 1L));

        proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE));

        assertEquals(ProposalStatus.AUTO_APPLIED, ready.getStatus());
        assertEquals(Map.of("recordId", 1L), ready.getResult());
        assertEquals(ProposalStatus.PENDING, blocked.getStatus());
        assertEquals(ProposalStatus.PENDING, manual.getStatus());
        verify(createRecordApplier, times(2)).apply(any());
        verify(auditService).record(argThat(e -> e.action() == AuditAction.PROPOSAL_AUTO_APPLIED && e.entityId() == 6L));
    }

    @Test
    void decideProposal_AutoApplyCascadesThroughChains() {
        graph();
        stored(5, ProposalStatus.PENDING);
        Proposal second = stored(6, ProposalStatus.PENDING);
        second.setAutoApplyWhenReady(true);
        Proposal third = stored(7, ProposalStatus.PENDING);
        third.setAutoApplyWhenReady(true);
        dependsOn(6, 5);
        dependsOn(7, 6);

        proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE));

        assertEquals(ProposalStatus.AUTO_APPLIED, second.getStatus());
        assertEquals(ProposalStatus.AUTO_APPLIED, third.getStatus());
    }

    @Test
    void decideProposal_AFailingDependentAutoApplyLeavesItPendingWithoutFailingTheApproval() {
        graph();
        stored(5, ProposalStatus.PENDING);
        Proposal dependent = stored(6, ProposalStatus.PENDING);
        dependent.setAutoApplyWhenReady(true);
        dependsOn(6, 5);
        lenient().when(createRecordApplier.apply(argThat(p -> p.getId().id() == 6L))).thenThrow(new IllegalArgumentException("payload is malformed"));

        ProposalDto result = proposalService.decideProposal(5L, new DecideProposalCommand(ProposalDecision.APPROVE));

        assertEquals(ProposalState.APPROVED, result.getStatus());
        assertEquals(ProposalStatus.PENDING, dependent.getStatus());
        assertFalse(dependent.isAutoApplyWhenReady());
    }

    @Test
    void addDependency_StoresTheEdge() {
        graph();
        stored(5, ProposalStatus.PENDING);
        stored(6, ProposalStatus.PENDING);

        proposalService.addDependency(6L, 5L);

        verify(dependencyRepository).save(argThat(edge -> edge.getId().proposalId() == 6L && edge.getId().dependsOnProposalId() == 5L));
    }

    @Test
    void addDependency_ExistingEdgeIsANoOp() {
        graph();
        stored(5, ProposalStatus.PENDING);
        stored(6, ProposalStatus.PENDING);
        dependsOn(6, 5);

        proposalService.addDependency(6L, 5L);

        verify(dependencyRepository, never()).save(any());
    }

    @Test
    void addDependency_SelfDependencyIsRejected() {
        graph();
        stored(5, ProposalStatus.PENDING);

        assertThrows(IllegalArgumentException.class, () -> proposalService.addDependency(5L, 5L));
        verify(dependencyRepository, never()).save(any());
    }

    @Test
    void addDependency_CyclesAreRejectedDirectAndIndirect() {
        graph();
        stored(5, ProposalStatus.PENDING);
        stored(6, ProposalStatus.PENDING);
        stored(7, ProposalStatus.PENDING);
        dependsOn(6, 5);
        dependsOn(7, 6);

        assertThrows(IllegalArgumentException.class, () -> proposalService.addDependency(5L, 6L));
        assertThrows(IllegalArgumentException.class, () -> proposalService.addDependency(5L, 7L));
        verify(dependencyRepository, never()).save(any());
    }

    @Test
    void addDependency_UnknownProposalsAreNotFound() {
        graph();
        stored(5, ProposalStatus.PENDING);

        assertThrows(ProposalNotFoundException.class, () -> proposalService.addDependency(5L, 99L));
        assertThrows(ProposalNotFoundException.class, () -> proposalService.addDependency(99L, 5L));
    }
}
