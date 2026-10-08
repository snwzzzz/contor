package com.example.contor.service;

import com.example.contor.api.model.DecideProposalCommand;
import com.example.contor.api.model.ProposalDecision;
import com.example.contor.api.model.ProposalDto;
import com.example.contor.api.model.ProposalPageDto;
import com.example.contor.api.model.ProposalState;
import com.example.contor.ai.ProposalDraft;
import com.example.contor.domain.ActorType;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.ProposalAlreadyDecidedException;
import com.example.contor.exception.ProposalNotApplicableException;
import com.example.contor.exception.ProposalNotFoundException;
import com.example.contor.exception.RecordValidationException;
import com.example.contor.persistence.AppUserRepository;
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
    private ProposalApplier createRecordApplier;

    private ProposalService proposalService;

    @BeforeEach
    void setup() {
        lenient().when(createRecordApplier.kind()).thenReturn(ProposalKind.CREATE_RECORD);
        proposalService = new ProposalService(proposalRepository, appUserRepository, emailMessageRepository, auditService,
                new ProposalApplierRegistry(List.of(createRecordApplier)));
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
        when(proposalRepository.findByStatus(ProposalStatus.PENDING, expected))
                .thenReturn(new PageImpl<>(List.of(Proposal.builder().kind(ProposalKind.SET_LABEL).payload(Map.of()).build()), expected, 11));

        ProposalPageDto page = proposalService.listProposals(ProposalState.PENDING, 1, 10);

        assertEquals(1, page.getItems().size());
        assertEquals(11L, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void listProposals_Unfiltered() {
        Pageable expected = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(proposalRepository.findAll(expected)).thenReturn(new PageImpl<>(List.of(), expected, 0));

        assertEquals(0, proposalService.listProposals(null, null, null).getTotalElements());
    }

    @Test
    void countProposals_DefaultsToPending() {
        when(proposalRepository.countByStatus(ProposalStatus.PENDING)).thenReturn(3L);

        assertEquals(3L, proposalService.countProposals(null).getCount());
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
}
