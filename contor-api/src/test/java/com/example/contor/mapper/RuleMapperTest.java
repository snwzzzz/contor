package com.example.contor.mapper;

import com.example.contor.api.model.RuleDto;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.Rule;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class RuleMapperTest {

    @Test
    void maps_rule_to_dto() {
        var rule = Rule.builder().id(new Rule.RuleId(4L)).name("Shop mails").enabled(false).priority(5)
                .conditions(Map.of("from", "shop@example.com")).actions(List.of(Map.of("type", "SET_LABEL")))
                .createdFromProposal(Proposal.builder().id(new Proposal.ProposalId(9L)).build()).build();

        RuleDto dto = RuleMapper.toDto(rule);

        assertEquals(4L, dto.getId());
        assertEquals("Shop mails", dto.getName());
        assertFalse(dto.getEnabled());
        assertEquals(5, dto.getPriority());
        assertEquals("shop@example.com", dto.getConditions().get("from"));
        assertEquals("SET_LABEL", dto.getActions().get(0).get("type"));
        assertEquals(9L, dto.getCreatedFromProposalId());
    }

    @Test
    void handles_null_input() {
        assertNull(RuleMapper.toDto(null));
    }

    @Test
    void handles_null_id_and_relations() {
        RuleDto dto = RuleMapper.toDto(Rule.builder().name("x").build());

        assertNull(dto.getId());
        assertNull(dto.getCreatedFromProposalId());
        assertTrue(dto.getConditions().isEmpty());
        assertTrue(dto.getActions().isEmpty());
    }
}
