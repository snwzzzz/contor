package com.example.contor.proposal;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ReplyDraft;
import com.example.contor.domain.ReplyDraftStatus;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ReplyDraftRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DraftReplyApplierTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private ReplyDraftRepository replyDraftRepository;

    @InjectMocks
    private DraftReplyApplier applier;

    private EmailMessage email;

    @BeforeEach
    void setUp() {
        var principal = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        AppUser owner = AppUser.builder().id(new AppUser.AppUserId(1L)).build();
        MailAccount account = MailAccount.builder().id(new MailAccount.MailAccountId(3L)).owner(owner).build();
        email = EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).account(account).conversationId("conv")
                .subject("Termin").receivedAt(Instant.parse("2026-10-06T08:00:00Z")).build();
        lenient().when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(8L))).thenReturn(Optional.of(email));
        lenient().when(replyDraftRepository.saveAndFlush(any(ReplyDraft.class))).thenAnswer(inv -> {
            ReplyDraft draft = inv.getArgument(0);
            draft.setId(new ReplyDraft.ReplyDraftId(42L));
            return draft;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("emailId", 8);
        payload.put("accountId", 3);
        payload.put("conversationId", "conv");
        payload.put("to", List.of(Map.of("name", "Anna Berger", "address", "anna@acme.com")));
        payload.put("cc", List.of(Map.of("address", "bob@acme.com")));
        payload.put("subject", "Re: Termin");
        payload.put("body", "Hallo Anna,\n\ngerne am [Tag].\n\nMax");
        payload.put("language", "de");
        payload.put("placeholders", List.of("[Tag]"));
        return payload;
    }

    private Proposal proposal(Map<String, Object> payload) {
        return Proposal.builder().id(new Proposal.ProposalId(5L)).kind(ProposalKind.DRAFT_REPLY).payload(payload)
                .finalPayload(payload).sourceEmail(email).build();
    }

    @Test
    void applies_draft_reply_proposals_without_needing_the_owner() {
        assertThat(applier.kind()).isEqualTo(ProposalKind.DRAFT_REPLY);
        assertThat(applier.requiresOwner()).isFalse();
    }

    @Test
    void keeps_the_final_text_as_a_ready_draft() {
        Proposal proposal = proposal(payload());

        Map<String, Object> result = applier.apply(proposal);

        assertThat(result).containsEntry("replyDraftId", 42L);
        ArgumentCaptor<ReplyDraft> saved = ArgumentCaptor.forClass(ReplyDraft.class);
        verify(replyDraftRepository).saveAndFlush(saved.capture());
        ReplyDraft draft = saved.getValue();
        assertThat(draft.getEmail()).isSameAs(email);
        assertThat(draft.getAccount()).isSameAs(email.getAccount());
        assertThat(draft.getConversationId()).isEqualTo("conv");
        assertThat(draft.getToRecipients()).containsExactly(new EmailRecipient("Anna Berger", "anna@acme.com"));
        assertThat(draft.getCcRecipients()).containsExactly(new EmailRecipient(null, "bob@acme.com"));
        assertThat(draft.getSubject()).isEqualTo("Re: Termin");
        assertThat(draft.getBody()).contains("gerne am [Tag]");
        assertThat(draft.getStatus()).isEqualTo(ReplyDraftStatus.READY);
        assertThat(draft.getProposal()).isSameAs(proposal);
    }

    @Test
    void a_missing_subject_answers_the_email() {
        Map<String, Object> payload = payload();
        payload.remove("subject");

        applier.apply(proposal(payload));

        ArgumentCaptor<ReplyDraft> saved = ArgumentCaptor.forClass(ReplyDraft.class);
        verify(replyDraftRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSubject()).isEqualTo("Re: Termin");
    }

    @Test
    void rejects_a_missing_body() {
        Map<String, Object> payload = payload();
        payload.put("body", "  ");

        assertThatThrownBy(() -> applier.apply(proposal(payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload.body");
        verify(replyDraftRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejects_missing_or_malformed_recipients() {
        Map<String, Object> empty = payload();
        empty.put("to", List.of());
        Map<String, Object> noAddress = payload();
        noAddress.put("to", List.of(Map.of("name", "Anna")));

        assertThatThrownBy(() -> applier.apply(proposal(empty))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload.to");
        assertThatThrownBy(() -> applier.apply(proposal(noAddress))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload.to");
    }

    @Test
    void an_email_of_another_user_is_not_found() {
        email.getAccount().setOwner(AppUser.builder().id(new AppUser.AppUserId(2L)).build());

        assertThatThrownBy(() -> applier.apply(proposal(payload()))).isInstanceOf(EmailNotFoundException.class);
        verify(replyDraftRepository, never()).saveAndFlush(any());
    }

    @Test
    void without_a_logged_in_user_nothing_is_kept() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> applier.apply(proposal(payload()))).isInstanceOf(EmailNotFoundException.class);
    }
}
