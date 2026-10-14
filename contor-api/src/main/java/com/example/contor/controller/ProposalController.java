package com.example.contor.controller;

import com.example.contor.api.ProposalsApi;
import com.example.contor.api.model.DecideProposalCommand;
import com.example.contor.api.model.ProposalCountDto;
import com.example.contor.api.model.ProposalDto;
import com.example.contor.api.model.ProposalGroup;
import com.example.contor.api.model.ProposalPageDto;
import com.example.contor.api.model.ProposalPriority;
import com.example.contor.api.model.ProposalSort;
import com.example.contor.api.model.ProposalState;
import com.example.contor.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ProposalController implements ProposalsApi {

    private final ProposalService proposalService;

    @Override
    public ResponseEntity<ProposalPageDto> listProposals(ProposalState status, ProposalGroup group, ProposalPriority minImportance,
                                                         ProposalSort sort, Integer page, Integer size) {
        return ResponseEntity.ok(proposalService.listProposals(status, group, minImportance, sort, page, size));
    }

    @Override
    public ResponseEntity<ProposalCountDto> countProposals(ProposalState status, ProposalGroup group, ProposalPriority minImportance) {
        return ResponseEntity.ok(proposalService.countProposals(status, group, minImportance));
    }

    @Override
    public ResponseEntity<ProposalDto> getProposal(Long proposalId) {
        return ResponseEntity.ok(proposalService.getProposal(proposalId));
    }

    @Override
    public ResponseEntity<ProposalDto> decideProposal(Long proposalId, DecideProposalCommand decideProposalCommand) {
        return ResponseEntity.ok(proposalService.decideProposal(proposalId, decideProposalCommand));
    }
}
