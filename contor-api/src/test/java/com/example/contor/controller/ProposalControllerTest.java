package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.DecideProposalCommand;
import com.example.contor.api.model.ProposalAction;
import com.example.contor.api.model.ProposalCountDto;
import com.example.contor.api.model.ProposalDecision;
import com.example.contor.api.model.ProposalDto;
import com.example.contor.api.model.ProposalPageDto;
import com.example.contor.api.model.ProposalState;
import com.example.contor.exception.ProposalAlreadyDecidedException;
import com.example.contor.exception.ProposalNotApplicableException;
import com.example.contor.domain.ProposalKind;
import com.example.contor.service.ProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(ProposalController.class)
public class ProposalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProposalService proposalService;

    private static ProposalDto dto(ProposalState state) {
        return new ProposalDto(5L, ProposalAction.CREATE_RECORD, state, Map.of("collectionId", 1), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void list_proposals_by_status() throws Exception {
        when(proposalService.listProposals(ProposalState.PENDING, 0, 25)).thenReturn(new ProposalPageDto(List.of(dto(ProposalState.PENDING)), 0, 25, 1L, 1));

        mockMvc.perform(get("/api/proposals").param("status", "PENDING").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("CREATE_RECORD"));
    }

    @Test
    void count_pending_proposals() throws Exception {
        when(proposalService.countProposals(null)).thenReturn(new ProposalCountDto(3L));

        mockMvc.perform(get("/api/proposals/count").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void decide_proposal_returns_the_decided_proposal() throws Exception {
        when(proposalService.decideProposal(eq(5L), any())).thenReturn(dto(ProposalState.APPROVED));

        mockMvc.perform(post("/api/proposals/5/decision").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecideProposalCommand(ProposalDecision.APPROVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void decide_proposal_returns_409_when_already_decided_or_not_applicable() throws Exception {
        when(proposalService.decideProposal(eq(5L), any())).thenThrow(new ProposalAlreadyDecidedException(5L));
        mockMvc.perform(post("/api/proposals/5/decision").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecideProposalCommand(ProposalDecision.REJECT))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Proposal 5 has already been decided"));

        when(proposalService.decideProposal(eq(6L), any())).thenThrow(new ProposalNotApplicableException(ProposalKind.CREATE_COLLECTION));
        mockMvc.perform(post("/api/proposals/6/decision").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecideProposalCommand(ProposalDecision.APPROVE))))
                .andExpect(status().isConflict());
    }

    @Test
    void decide_proposal_requires_a_decision() throws Exception {
        mockMvc.perform(post("/api/proposals/5/decision").with(TestAuth.user()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(proposalService);
    }
}
