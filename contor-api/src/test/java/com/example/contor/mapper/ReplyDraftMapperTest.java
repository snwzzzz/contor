package com.example.contor.mapper;

import com.example.contor.api.model.RecipientDto;
import com.example.contor.api.model.ReplyDraftDto;
import com.example.contor.api.model.ReplyDraftState;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ReplyDraft;
import com.example.contor.domain.ReplyDraftStatus;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReplyDraftMapperTest {

    @Test
    void maps_every_field() {
        ReplyDraft draft = ReplyDraft.builder().id(new ReplyDraft.ReplyDraftId(4L))
                .email(EmailMessage.builder().id(new EmailMessage.EmailMessageId(8L)).build())
                .account(MailAccount.builder().id(new MailAccount.MailAccountId(3L)).build())
                .conversationId("conv")
                .toRecipients(List.of(new EmailRecipient("Anna", "anna@acme.com")))
                .ccRecipients(List.of(new EmailRecipient(null, "bob@acme.com")))
                .subject("Re: Termin").body("Gerne").status(ReplyDraftStatus.READY)
                .proposal(Proposal.builder().id(new Proposal.ProposalId(5L)).build())
                .build();
        draft.setCreatedAt(Instant.parse("2026-10-06T08:00:00Z"));

        ReplyDraftDto dto = ReplyDraftMapper.toDto(draft);

        assertThat(dto.getId()).isEqualTo(4L);
        assertThat(dto.getEmailId()).isEqualTo(8L);
        assertThat(dto.getAccountId()).isEqualTo(3L);
        assertThat(dto.getConversationId()).isEqualTo("conv");
        assertThat(dto.getToRecipients()).extracting(RecipientDto::getAddress).containsExactly("anna@acme.com");
        assertThat(dto.getCcRecipients()).extracting(RecipientDto::getName).containsExactly((String) null);
        assertThat(dto.getSubject()).isEqualTo("Re: Termin");
        assertThat(dto.getBody()).isEqualTo("Gerne");
        assertThat(dto.getStatus()).isEqualTo(ReplyDraftState.READY);
        assertThat(dto.getProposalId()).isEqualTo(5L);
        assertThat(dto.getCreatedAt()).hasToString("2026-10-06T08:00Z");
    }

    @Test
    void null_in_null_out_and_missing_relations_stay_null() {
        assertThat(ReplyDraftMapper.toDto(null)).isNull();
        ReplyDraftDto dto = ReplyDraftMapper.toDto(ReplyDraft.builder().subject("s").body("b").build());
        assertThat(dto.getEmailId()).isNull();
        assertThat(dto.getProposalId()).isNull();
        assertThat(dto.getToRecipients()).isEmpty();
    }

    @Test
    void converts_recipients_from_the_api() {
        assertThat(ReplyDraftMapper.toRecipients(List.of(new RecipientDto().name("Anna").address("anna@acme.com"))))
                .containsExactly(new EmailRecipient("Anna", "anna@acme.com"));
        assertThat(ReplyDraftMapper.toRecipients(null)).isNull();
    }
}
