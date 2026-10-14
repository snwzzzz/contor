package com.example.contor.mapper;

import com.example.contor.api.model.RuleDto;
import com.example.contor.domain.Rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class RuleMapper {

    public static RuleDto toDto(Rule rule) {
        if (rule == null) return null;

        return new RuleDto()
                .id(rule.getId() != null ? rule.getId().id() : null)
                .name(rule.getName())
                .enabled(rule.isEnabled())
                .priority(rule.getPriority())
                .conditions(rule.getConditions() != null ? new LinkedHashMap<>(rule.getConditions()) : new LinkedHashMap<>())
                .actions(rule.getActions() != null ? new ArrayList<>(rule.getActions()) : new ArrayList<>())
                .createdFromProposalId(rule.getCreatedFromProposal() != null && rule.getCreatedFromProposal().getId() != null
                        ? rule.getCreatedFromProposal().getId().id() : null);
    }
}
