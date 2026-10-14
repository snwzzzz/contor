package com.example.contor.service;

import com.example.contor.api.model.RecipientDto;
import com.example.contor.api.model.ReplyDraftDto;
import com.example.contor.api.model.ReplyDraftState;
import com.example.contor.api.model.UpdateReplyDraftCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.ReplyDraft;
import com.example.contor.domain.ReplyDraftStatus;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.ReplyDraftNotEditableException;
import com.example.contor.exception.ReplyDraftNotFoundException;
import com.example.contor.persistence.ReplyDraftRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplyDraftServiceTest {

    @Mock
    private ReplyDraftRepository replyDraftRepository;

    @InjectMocks
    private ReplyDraftService service;

    @BeforeEach
    void login() {
        var principal = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        lenient().when(replyDraftRepository.saveAndFlush(any(ReplyDraft.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private ReplyDraft draft(long ownerId, ReplyDraftStatus status) {
        ReplyDraft draft = ReplyDraft.builder().id(new ReplyDraft.ReplyDraftId(4L))
                .email(EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).build())
                .account(MailAccount.builder().id(new MailAccount.MailAccountId(3L))
                        .owner(AppUser.builder().id(new AppUser.AppUserId(ownerId)).build()).build())
                .toRecipients(new ArrayList<>(List.of(new EmailRecipient("Anna", "anna@acme.com"))))
                .subject("Re: Termin").body("Gerne").status(status).build();
        when(replyDraftRepository.findById(new ReplyDraft.ReplyDraftId(4L))).thenReturn(Optional.of(draft));
        return draft;
    }

    @Test
    void updateReplyDraft_ChangesOnlyTheGivenParts() {
        draft(1L, ReplyDraftStatus.READY);

        ReplyDraftDto dto = service.updateReplyDraft(4L, new UpdateReplyDraftCommand().body("  Gerne, bis Freitag!  "));

        assertThat(dto.getBody()).isEqualTo("Gerne, bis Freitag!");
        assertThat(dto.getSubject()).isEqualTo("Re: Termin");
        assertThat(dto.getToRecipients()).extracting(RecipientDto::getAddress).containsExactly("anna@acme.com");
    }

    @Test
    void updateReplyDraft_ReplacesRecipients() {
        draft(1L, ReplyDraftStatus.READY);

        ReplyDraftDto dto = service.updateReplyDraft(4L, new UpdateReplyDraftCommand()
                .toRecipients(List.of(new RecipientDto().address("bob@acme.com")))
                .ccRecipients(List.of()));

        assertThat(dto.getToRecipients()).extracting(RecipientDto::getAddress).containsExactly("bob@acme.com");
        assertThat(dto.getCcRecipients()).isEmpty();
    }

    @Test
    void updateReplyDraft_EmptyBodyOrRecipientsRejected() {
        draft(1L, ReplyDraftStatus.READY);

        assertThatThrownBy(() -> service.updateReplyDraft(4L, new UpdateReplyDraftCommand().body(" ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateReplyDraft(4L, new UpdateReplyDraftCommand().toRecipients(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(replyDraftRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateReplyDraft_DiscardedDraftRejected() {
        draft(1L, ReplyDraftStatus.DISCARDED);

        assertThatThrownBy(() -> service.updateReplyDraft(4L, new UpdateReplyDraftCommand().body("x")))
                .isInstanceOf(ReplyDraftNotEditableException.class);
    }

    @Test
    void updateReplyDraft_ForeignDraftNotFound() {
        draft(2L, ReplyDraftStatus.READY);

        assertThatThrownBy(() -> service.updateReplyDraft(4L, new UpdateReplyDraftCommand().body("x")))
                .isInstanceOf(ReplyDraftNotFoundException.class);
        verify(replyDraftRepository, never()).saveAndFlush(any());
    }

    @Test
    void discardReplyDraft_MarksItDiscarded() {
        ReplyDraft draft = draft(1L, ReplyDraftStatus.READY);

        assertThat(service.discardReplyDraft(4L).getStatus()).isEqualTo(ReplyDraftState.DISCARDED);
        assertThat(draft.getStatus()).isEqualTo(ReplyDraftStatus.DISCARDED);
    }

    @Test
    void discardReplyDraft_ForeignDraftNotFound() {
        draft(2L, ReplyDraftStatus.READY);

        assertThatThrownBy(() -> service.discardReplyDraft(4L)).isInstanceOf(ReplyDraftNotFoundException.class);
    }
}
