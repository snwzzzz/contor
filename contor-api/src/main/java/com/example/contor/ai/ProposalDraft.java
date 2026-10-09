package com.example.contor.ai;

import com.example.contor.domain.ProposalKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A proposal suggested by the AI before it is stored; payload formats are documented with the spec's ProposalAction.
 *
 * @param ref                  local name of this draft within one email's answer (e.g. "p1"), so later drafts can point at it
 * @param dependsOnRefs        refs of drafts (same email) that must be applied first; the worker turns them into proposal
 *                             ids and replaces {@code {"ref": "p1"}} in the payload by {@code {"proposalId": id}}
 * @param dependsOnProposalIds already stored proposals this draft needs (e.g. a pending record of an earlier email)
 */
public record ProposalDraft(ProposalKind kind, Map<String, Object> payload, BigDecimal confidence, String reasoning,
                            String ref, List<String> dependsOnRefs, List<Long> dependsOnProposalIds) {

    public ProposalDraft {
        dependsOnRefs = dependsOnRefs != null ? dependsOnRefs : List.of();
        dependsOnProposalIds = dependsOnProposalIds != null ? dependsOnProposalIds : List.of();
    }

    /** A draft that stands alone. */
    public ProposalDraft(ProposalKind kind, Map<String, Object> payload, BigDecimal confidence, String reasoning) {
        this(kind, payload, confidence, reasoning, null, List.of(), List.of());
    }
}
