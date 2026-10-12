package com.example.contor.mapper;

import com.example.contor.api.model.ProposalAction;
import com.example.contor.api.model.ProposalDto;
import com.example.contor.api.model.ProposalPriority;
import com.example.contor.api.model.ProposalState;
import com.example.contor.domain.Proposal;

import java.util.LinkedHashMap;

public class ProposalMapper {

    public static ProposalDto toDto(Proposal proposal) {
        if (proposal == null) return null;

        return new ProposalDto()
                .id(proposal.getId() != null ? proposal.getId().id() : null)
                .kind(proposal.getKind() != null ? ProposalAction.valueOf(proposal.getKind().name()) : null)
                .status(proposal.getStatus() != null ? ProposalState.valueOf(proposal.getStatus().name()) : null)
                .payload(proposal.getPayload() != null ? new LinkedHashMap<>(proposal.getPayload()) : new LinkedHashMap<>())
                .finalPayload(proposal.getFinalPayload() != null ? new LinkedHashMap<>(proposal.getFinalPayload()) : null)
                .sourceEmailId(proposal.getSourceEmail() != null && proposal.getSourceEmail().getId() != null
                        ? proposal.getSourceEmail().getId().id() : null)
                .confidence(MapperSupport.normalize(proposal.getConfidence()))
                .reasoning(proposal.getReasoning())
                .importance(proposal.getImportance() != null ? ProposalPriority.valueOf(proposal.getImportance().name()) : null)
                .importanceScore(proposal.getImportanceScore())
                .importanceReason(proposal.getImportanceReason())
                .model(proposal.getModel())
                .promptVersion(proposal.getPromptVersion())
                .schemaVersion(proposal.getSchemaVersion())
                .decidedAt(MapperSupport.toOffset(proposal.getDecidedAt()))
                .decidedById(proposal.getDecidedBy() != null && proposal.getDecidedBy().getId() != null
                        ? proposal.getDecidedBy().getId().id() : null)
                .createdAt(MapperSupport.toOffset(proposal.getCreatedAt()));
    }
}
