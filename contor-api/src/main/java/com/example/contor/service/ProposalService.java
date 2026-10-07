package com.example.contor.service;

import com.example.contor.api.model.DecideProposalCommand;
import com.example.contor.api.model.ProposalCountDto;
import com.example.contor.api.model.ProposalDecision;
import com.example.contor.api.model.ProposalDto;
import com.example.contor.api.model.ProposalPageDto;
import com.example.contor.api.model.ProposalState;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.exception.ProposalAlreadyDecidedException;
import com.example.contor.exception.ProposalNotApplicableException;
import com.example.contor.exception.ProposalNotFoundException;
import com.example.contor.mapper.ProposalMapper;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.proposal.ProposalApplier;
import com.example.contor.proposal.ProposalApplierRegistry;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lists proposals and decides them. Approving runs the kind's {@link ProposalApplier} in the same transaction, so a
 * failing application leaves the proposal PENDING. Every decision is audited.
 */
@RequiredArgsConstructor
@Service
public class ProposalService {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final ProposalRepository proposalRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final ProposalApplierRegistry proposalApplierRegistry;

    @Transactional
    public ProposalPageDto listProposals(ProposalState status, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Proposal> result = status == null
                ? proposalRepository.findAll(pageable)
                : proposalRepository.findByStatus(ProposalStatus.valueOf(status.name()), pageable);
        return new ProposalPageDto(new ArrayList<>(result.getContent().stream().map(ProposalMapper::toDto).toList()),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ProposalCountDto countProposals(ProposalState status) {
        ProposalStatus counted = status != null ? ProposalStatus.valueOf(status.name()) : ProposalStatus.PENDING;
        return new ProposalCountDto(proposalRepository.countByStatus(counted));
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

        AuditAction action;
        if (command.getDecision() == ProposalDecision.REJECT) {
            if (command.getPayload() != null) {
                throw new IllegalArgumentException("payload is only allowed when approving");
            }
            proposal.setStatus(ProposalStatus.REJECTED);
            action = AuditAction.PROPOSAL_REJECTED;
        } else {
            ProposalApplier applier = proposalApplierRegistry.applier(proposal.getKind())
                    .orElseThrow(() -> new ProposalNotApplicableException(proposal.getKind()));
            if (applier.requiresOwner() && !CurrentActor.isOwner()) {
                throw new AccessDeniedException("Only the owner may approve " + proposal.getKind() + " proposals");
            }
            boolean edited = command.getPayload() != null;
            proposal.setFinalPayload(new LinkedHashMap<>(edited ? command.getPayload() : proposal.getPayload()));
            applier.apply(proposal);
            proposal.setStatus(edited ? ProposalStatus.EDITED_AND_APPROVED : ProposalStatus.APPROVED);
            action = AuditAction.PROPOSAL_APPROVED;
        }
        proposal.setDecidedAt(Instant.now());
        proposal.setDecidedBy(CurrentActor.userId()
                .map(userId -> appUserRepository.getReferenceById(new AppUser.AppUserId(userId)))
                .orElse(null));
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
        return ProposalMapper.toDto(saved);
    }

    private Proposal load(Long id) {
        return proposalRepository.findById(new Proposal.ProposalId(id))
                .orElseThrow(() -> new ProposalNotFoundException(id));
    }
}
