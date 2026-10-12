package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
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

        assertThat(proposalRepository.findPendingCreateRecordIdWithTitle(4L, "send cv")).isPresent();
        assertThat(proposalRepository.findPendingCreateRecordIdWithTitle(3L, "Send CV")).isEmpty();
        assertThat(proposalRepository.findPendingCreateRecordIdWithTitle(5L, "Rejected")).isEmpty();
        assertThat(proposalRepository.findPendingCreateRecordIdWithTitle(6L, "Update")).isEmpty();
        assertThat(proposalRepository.findPendingCreateRecordIdWithValue(4L, "email", "anna@acme.com")).isPresent();
        assertThat(proposalRepository.findPendingCreateRecordIdWithValue(4L, "email", "bob@acme.com")).isEmpty();
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

    @Test
    void returns_the_id_of_the_pending_record_proposal() {
        var pending = proposalRepository.saveAndFlush(Proposal.builder().kind(ProposalKind.CREATE_RECORD)
                .payload(Map.of("collectionId", 2, "title", "Acme", "values", Map.of("domain", "acme.com"))).build());

        assertThat(proposalRepository.findPendingCreateRecordIdWithValue(2L, "domain", "ACME.com")).contains(pending.getId().id());
        assertThat(proposalRepository.findPendingCreateRecordIdWithTitle(2L, "acme")).contains(pending.getId().id());
    }

    @Test
    void relation_proposals_are_findings() {
        proposalRepository.save(of(ProposalKind.CREATE_RELATION, ProposalStatus.PENDING,
                Map.of("relationTypeId", 10, "from", Map.of("proposalId", 1), "to", Map.of("proposalId", 2))));
        proposalRepository.saveAndFlush(of(ProposalKind.SET_LABEL, ProposalStatus.PENDING, Map.of("labelId", 3)));
        var actionCollections = List.of(3L, 4L);

        assertThat(proposalRepository.countFindings("PENDING", actionCollections)).isEqualTo(1);
        assertThat(proposalRepository.countActions("PENDING", actionCollections)).isEqualTo(1);
        assertThat(proposalRepository.findFindings("PENDING", actionCollections, PageRequest.of(0, 10)).getContent())
                .extracting(Proposal::getKind).containsExactly(ProposalKind.CREATE_RELATION);
    }

    private EmailMessage message(MailAccount account, String id, String conversationId, String folder, String receivedAt) {
        EmailMessage email = Fixtures.email(account, id);
        email.setConversationId(conversationId);
        email.setFolder(folder);
        email.setReceivedAt(Instant.parse(receivedAt));
        return emailMessageRepository.save(email);
    }

    private Proposal draftReply(EmailMessage source, ProposalStatus status) {
        return proposalRepository.save(Proposal.builder().kind(ProposalKind.DRAFT_REPLY).status(status)
                .payload(Map.of("body", "Thanks")).sourceEmail(source).build());
    }

    @Test
    void finds_pending_reply_drafts_of_a_conversation_and_of_emails() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));
        MailAccount account = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        EmailMessage first = message(account, "a", "conv-a", "inbox", "2026-01-01T10:00:00Z");
        EmailMessage elsewhere = message(account, "b", "conv-b", "inbox", "2026-01-01T10:00:00Z");
        Proposal pending = draftReply(first, ProposalStatus.PENDING);
        draftReply(first, ProposalStatus.REJECTED);
        draftReply(elsewhere, ProposalStatus.PENDING);
        proposalRepository.flush();

        assertThat(proposalRepository.findForConversation(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, account.getId(), "conv-a"))
                .extracting(Proposal::getId).containsExactly(pending.getId());
        assertThat(proposalRepository.findByKindAndStatusAndSourceEmail_IdIn(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING,
                List.of(first.getId()))).extracting(Proposal::getId).containsExactly(pending.getId());
    }

    @Test
    void finds_reply_drafts_overtaken_by_a_newer_sent_or_received_message() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));
        MailAccount account = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        EmailMessage answered = message(account, "a1", "conv-a", "inbox", "2026-01-01T10:00:00Z");
        message(account, "a2", "conv-a", "sent", "2026-01-01T11:00:00Z");
        EmailMessage followedUp = message(account, "b1", "conv-b", "inbox", "2026-01-01T10:00:00Z");
        message(account, "b2", "conv-b", "inbox", "2026-01-02T10:00:00Z");
        EmailMessage newest = message(account, "c1", "conv-c", "inbox", "2026-01-03T10:00:00Z");
        message(account, "c0", "conv-c", "sent", "2026-01-02T10:00:00Z");
        Proposal answeredDraft = draftReply(answered, ProposalStatus.PENDING);
        Proposal followedUpDraft = draftReply(followedUp, ProposalStatus.PENDING);
        draftReply(newest, ProposalStatus.PENDING);
        proposalRepository.flush();

        assertThat(proposalRepository.findWithNewerSent(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, account.getId()))
                .extracting(Proposal::getId).containsExactly(answeredDraft.getId());
        assertThat(proposalRepository.findWithNewerReceived(ProposalKind.DRAFT_REPLY, ProposalStatus.PENDING, account.getId()))
                .extracting(Proposal::getId).containsExactly(followedUpDraft.getId());
    }
}

