package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Test
    void pages_proposals_by_status() {
        for (int i = 0; i < 3; i++) proposalRepository.save(proposal(ProposalStatus.PENDING));
        proposalRepository.save(proposal(ProposalStatus.APPROVED));

        var page = proposalRepository.findByStatus(ProposalStatus.PENDING, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void finds_pending_record_proposals_by_title_or_value() {
        proposalRepository.save(Proposal.builder().kind(ProposalKind.CREATE_RECORD)
                .payload(Map.of("collectionId", 4, "title", "Send CV", "values", Map.of("email", "Anna@Acme.com"))).build());
        proposalRepository.save(Proposal.builder().kind(ProposalKind.CREATE_RECORD).status(ProposalStatus.REJECTED)
                .payload(Map.of("collectionId", 5, "title", "Rejected", "values", Map.of())).build());
        proposalRepository.saveAndFlush(Proposal.builder().kind(ProposalKind.UPDATE_RECORD)
                .payload(Map.of("collectionId", 6, "recordId", 1, "title", "Update")).build());

        assertThat(proposalRepository.existsPendingCreateRecordWithTitle(4L, "send cv")).isTrue();
        assertThat(proposalRepository.existsPendingCreateRecordWithTitle(3L, "Send CV")).isFalse();
        assertThat(proposalRepository.existsPendingCreateRecordWithTitle(5L, "Rejected")).isFalse();
        assertThat(proposalRepository.existsPendingCreateRecordWithTitle(6L, "Update")).isFalse();
        assertThat(proposalRepository.existsPendingCreateRecordWithValue(4L, "email", "anna@acme.com")).isTrue();
        assertThat(proposalRepository.existsPendingCreateRecordWithValue(4L, "email", "bob@acme.com")).isFalse();
    }
}

