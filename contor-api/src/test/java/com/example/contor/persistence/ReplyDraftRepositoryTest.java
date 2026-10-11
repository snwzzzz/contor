package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ReplyDraft;
import com.example.contor.domain.ReplyDraftStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class ReplyDraftRepositoryTest {

    @Autowired
    private ReplyDraftRepository replyDraftRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MailAccount account;
    private EmailMessage email;

    @BeforeEach
    void setUp() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));
        account = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        email = emailMessageRepository.save(Fixtures.email(account, "m1"));
    }

    private ReplyDraft draft(EmailMessage answered, ReplyDraftStatus status) {
        return ReplyDraft.builder().email(answered).account(account).conversationId(answered.getConversationId())
                .toRecipients(List.of(new EmailRecipient("Shop", "shop@example.com")))
                .subject("Re: Your order has shipped").body("Thanks!").status(status).build();
    }

    @Test
    void stores_a_draft_with_recipients_and_its_proposal() {
        Proposal proposal = proposalRepository.save(Proposal.builder().kind(ProposalKind.DRAFT_REPLY).payload(Map.of("body", "Thanks!")).build());
        ReplyDraft draft = draft(email, ReplyDraftStatus.READY);
        draft.setProposal(proposal);

        ReplyDraft saved = replyDraftRepository.saveAndFlush(draft);

        ReplyDraft loaded = replyDraftRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getToRecipients()).containsExactly(new EmailRecipient("Shop", "shop@example.com"));
        assertThat(loaded.getCcRecipients()).isEmpty();
        assertThat(loaded.getProposal().getId()).isEqualTo(proposal.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void finds_drafts_of_a_thread_by_status() {
        EmailMessage other = emailMessageRepository.save(Fixtures.email(account, "m2"));
        replyDraftRepository.save(draft(email, ReplyDraftStatus.READY));
        replyDraftRepository.save(draft(email, ReplyDraftStatus.DISCARDED));
        replyDraftRepository.save(draft(other, ReplyDraftStatus.READY));
        replyDraftRepository.flush();

        assertThat(replyDraftRepository.findByEmail_IdInAndStatusOrderByCreatedAtAsc(List.of(email.getId()), ReplyDraftStatus.READY))
                .hasSize(1);
        assertThat(replyDraftRepository.findEmailIdsWithStatus(List.of(email.getId().id(), other.getId().id()), ReplyDraftStatus.READY))
                .containsExactlyInAnyOrder(email.getId().id(), other.getId().id());
    }

    @Test
    void rejects_an_unknown_status() {
        ReplyDraft saved = replyDraftRepository.saveAndFlush(draft(email, ReplyDraftStatus.READY));

        assertThatThrownBy(() -> jdbcTemplate.update("update reply_draft set status = 'SENT' where id = ?", saved.getId().id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
