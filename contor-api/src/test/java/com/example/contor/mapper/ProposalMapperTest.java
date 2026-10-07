package com.example.contor.mapper;

import com.example.contor.api.model.ProposalAction;
import com.example.contor.api.model.ProposalDto;
import com.example.contor.api.model.ProposalState;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ProposalMapperTest {

    @Test
    void maps_proposal_to_dto() {
        var proposal = Proposal.builder().id(new Proposal.ProposalId(5L)).kind(ProposalKind.CREATE_RECORD)
                .status(ProposalStatus.EDITED_AND_APPROVED).payload(Map.of("collectionId", 1)).finalPayload(Map.of("collectionId", 2))
                .sourceEmail(EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).build())
                .confidence(new BigDecimal("0.8100")).reasoning("Shipping notice").model("qwen3").promptVersion("extract-v1")
                .schemaVersion(3).decidedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .decidedBy(AppUser.builder().id(new AppUser.AppUserId(1L)).build()).build();
        proposal.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        ProposalDto dto = ProposalMapper.toDto(proposal);

        assertEquals(5L, dto.getId());
        assertEquals(ProposalAction.CREATE_RECORD, dto.getKind());
        assertEquals(ProposalState.EDITED_AND_APPROVED, dto.getStatus());
        assertEquals(1, dto.getPayload().get("collectionId"));
        assertEquals(2, dto.getFinalPayload().get("collectionId"));
        assertEquals(8L, dto.getSourceEmailId());
        assertEquals(new BigDecimal("0.81"), dto.getConfidence());
        assertEquals("Shipping notice", dto.getReasoning());
        assertEquals("qwen3", dto.getModel());
        assertEquals("extract-v1", dto.getPromptVersion());
        assertEquals(3, dto.getSchemaVersion());
        assertEquals(Instant.parse("2026-01-02T00:00:00Z").atOffset(ZoneOffset.UTC), dto.getDecidedAt());
        assertEquals(1L, dto.getDecidedById());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC), dto.getCreatedAt());
    }

    @Test
    void handles_null_input_and_relations() {
        assertNull(ProposalMapper.toDto(null));
        ProposalDto dto = ProposalMapper.toDto(Proposal.builder().kind(ProposalKind.SET_LABEL).payload(Map.of()).build());
        assertNull(dto.getId());
        assertNull(dto.getSourceEmailId());
        assertNull(dto.getDecidedById());
        assertNull(dto.getConfidence());
    }
}
