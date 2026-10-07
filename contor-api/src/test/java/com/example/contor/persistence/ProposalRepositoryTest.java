package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class ProposalRepositoryTest {

    @Autowired
    private ProposalRepository proposalRepository;

    private Proposal proposal(ProposalStatus status) {
        return Proposal.builder().kind(ProposalKind.CREATE_RECORD).status(status)
                .payload(Map.of("collectionId", 1, "values", Map.of("title", "Parcel")))
                .confidence(new BigDecimal("0.9")).model("qwen3").promptVersion("extract-v1").build();
    }

    @Test
    void finds_and_counts_by_status() {
        proposalRepository.save(proposal(ProposalStatus.PENDING));
        proposalRepository.save(proposal(ProposalStatus.PENDING));
        proposalRepository.save(proposal(ProposalStatus.REJECTED));

        assertThat(proposalRepository.countByStatus(ProposalStatus.PENDING)).isEqualTo(2);
        var pending = proposalRepository.findByStatusOrderByCreatedAtDesc(ProposalStatus.PENDING);
        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).getPayload()).containsKey("values");
    }

    @Test
    void new_proposals_default_to_pending() {
        var saved = proposalRepository.saveAndFlush(Proposal.builder().kind(ProposalKind.SET_LABEL).payload(Map.of()).build());

        assertThat(saved.getStatus()).isEqualTo(ProposalStatus.PENDING);
    }
}
