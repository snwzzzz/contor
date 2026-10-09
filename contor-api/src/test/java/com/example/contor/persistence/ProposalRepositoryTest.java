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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class ProposalRepositoryTest {

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

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

    private static Proposal of(ProposalKind kind, ProposalStatus status, Map<String, Object> payload) {
        return Proposal.builder().kind(kind).status(status).payload(payload).build();
    }

    @Test
    void splits_proposals_into_findings_and_actions() {
        // Collections 3 (calendar) and 4 (to-dos) hold actions; every other collection holds findings.
        proposalRepository.save(of(ProposalKind.CREATE_RECORD, ProposalStatus.PENDING, Map.of("collectionId", 1, "title", "Anna")));
        proposalRepository.save(of(ProposalKind.UPDATE_RECORD, ProposalStatus.PENDING, Map.of("collectionId", 7, "recordId", 2)));
        proposalRepository.save(of(ProposalKind.CREATE_RECORD, ProposalStatus.PENDING, Map.of("collectionId", 3, "title", "Interview")));
        proposalRepository.save(of(ProposalKind.CREATE_RECORD, ProposalStatus.PENDING, Map.of("collectionId", 4, "title", "Send CV")));
        proposalRepository.save(of(ProposalKind.CREATE_COLLECTION, ProposalStatus.PENDING, Map.of("slug", "jobs", "name", "Jobs")));
        proposalRepository.saveAndFlush(of(ProposalKind.CREATE_RECORD, ProposalStatus.REJECTED, Map.of("collectionId", 1, "title", "Old")));
        List<Long> actionCollections = List.of(3L, 4L);

        var findings = proposalRepository.findFindings("PENDING", actionCollections, PageRequest.of(0, 10));
        var actions = proposalRepository.findActions("PENDING", actionCollections, PageRequest.of(0, 10));

        assertThat(findings.getContent()).extracting(p -> p.getPayload().get("collectionId")).containsExactlyInAnyOrder(1, 7);
        assertThat(findings.getTotalElements()).isEqualTo(2);
        assertThat(actions.getContent()).extracting(Proposal::getKind)
                .containsExactlyInAnyOrder(ProposalKind.CREATE_RECORD, ProposalKind.CREATE_RECORD, ProposalKind.CREATE_COLLECTION);
        assertThat(proposalRepository.countFindings("PENDING", actionCollections)).isEqualTo(2);
        assertThat(proposalRepository.countActions("PENDING", actionCollections)).isEqualTo(3);
        assertThat(proposalRepository.countFindings("REJECTED", actionCollections)).isEqualTo(1);
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

    @Test
    void finds_proposals_by_source_email() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var first = emailMessageRepository.save(Fixtures.email(account, "m1"));
        var second = emailMessageRepository.save(Fixtures.email(account, "m2"));
        var other = emailMessageRepository.save(Fixtures.email(account, "m3"));
        var fromFirst = proposal(ProposalStatus.APPROVED);
        fromFirst.setSourceEmail(first);
        var fromSecond = proposal(ProposalStatus.REJECTED);
        fromSecond.setSourceEmail(second);
        var fromOther = proposal(ProposalStatus.APPROVED);
        fromOther.setSourceEmail(other);
        proposalRepository.saveAll(List.of(fromFirst, fromSecond, fromOther, proposal(ProposalStatus.PENDING)));

        var found = proposalRepository.findBySourceEmail_IdIn(List.of(first.getId(), second.getId()));

        assertThat(found).extracting(Proposal::getStatus).containsExactlyInAnyOrder(ProposalStatus.APPROVED, ProposalStatus.REJECTED);
    }
}

