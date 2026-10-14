package com.example.contor.service;

import com.example.contor.domain.AuditAction;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.persistence.ProposalRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps pending DRAFT_REPLY proposals current: a draft is out of date once you answered in Outlook (a newer sent message
 * in the conversation) or a newer email arrived (that one may get its own draft). Out-of-date drafts are REJECTED by
 * the system with a {@code reason} in their result, audited as {@link AuditAction#PROPOSAL_SUPERSEDED}.
 */
@RequiredArgsConstructor
@Service
public class ReplyLifecycleService {

    public static final String ANSWERED = "answered in Outlook";
    public static final String NEWER_EMAIL = "a newer email arrived in the conversation";
    public static final String REPLACED = "replaced by a newer suggestion";

    private final ProposalRepository proposalRepository;
    private final AuditService auditService;
    private final Clock clock;

    /** After a sync of the mailbox: rejects the pending drafts that were answered or overtaken. Returns how many. */
    @Transactional
    public int retireOvertaken(MailAccount.MailAccountId accountId) {
        int retired = 0;
        for (Proposal proposal : proposalRepository.findWithNewerSent(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, accountId)) {
            supersede(proposal, ANSWERED);
            retired++;
        }
        for (Proposal proposal : proposalRepository.findWithNewerReceived(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, accountId)) {
            if (proposal.getStatus() == ProposalStatus.PENDING) {
                supersede(proposal, NEWER_EMAIL);
                retired++;
            }
        }
        return retired;
    }

    /** Rejects every pending draft of the conversation (a new one replaces them). */
    @Transactional
    public void supersedeConversation(MailAccount.MailAccountId accountId, String conversationId, String reason) {
        if (conversationId == null) {
            return;
        }
        proposalRepository.findForConversation(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, accountId, conversationId)
                .forEach(proposal -> supersede(proposal, reason));
    }

    private void supersede(Proposal proposal, String reason) {
        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setAutoApplyWhenReady(false);
        proposal.setDecidedAt(clock.instant());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reason", reason);
        proposal.setResult(result);
        Proposal saved = proposalRepository.saveAndFlush(proposal);
        auditService.record(AuditEvent.system(AuditAction.PROPOSAL_SUPERSEDED)
                .entity("proposal", saved.getId() != null ? saved.getId().id() : null)
                .before(Map.of("status", ProposalStatus.PENDING.name()))
                .after(Map.of("status", ProposalStatus.REJECTED.name(), "kind", saved.getKind().name(), "reason", reason)));
    }
}
