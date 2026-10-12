package com.example.contor.mapper;

import com.example.contor.api.model.RecipientDto;
import com.example.contor.api.model.ReplyDraftDto;
import com.example.contor.api.model.ReplyDraftState;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ReplyDraft;

import java.util.ArrayList;
import java.util.List;

public class ReplyDraftMapper {

    public static ReplyDraftDto toDto(ReplyDraft draft) {
        if (draft == null) return null;

        return new ReplyDraftDto()
                .id(draft.getId() != null ? draft.getId().id() : null)
                .emailId(draft.getEmail() != null && draft.getEmail().getId() != null ? draft.getEmail().getId().id() : null)
                .accountId(draft.getAccount() != null && draft.getAccount().getId() != null ? draft.getAccount().getId().id() : null)
                .conversationId(draft.getConversationId())
                .toRecipients(recipients(draft.getToRecipients()))
                .ccRecipients(recipients(draft.getCcRecipients()))
                .subject(draft.getSubject())
                .body(draft.getBody())
                .status(draft.getStatus() != null ? ReplyDraftState.valueOf(draft.getStatus().name()) : null)
                .proposalId(draft.getProposal() != null && draft.getProposal().getId() != null ? draft.getProposal().getId().id() : null)
                .createdAt(MapperSupport.toOffset(draft.getCreatedAt()))
                .lastModified(MapperSupport.toOffset(draft.getLastModified()));
    }

    public static List<EmailRecipient> toRecipients(List<RecipientDto> recipients) {
        if (recipients == null) return null;
        return recipients.stream().map(recipient -> new EmailRecipient(recipient.getName(), recipient.getAddress())).toList();
    }

    private static List<RecipientDto> recipients(List<EmailRecipient> recipients) {
        if (recipients == null) return new ArrayList<>();
        return new ArrayList<>(recipients.stream().map(r -> new RecipientDto().name(r.name()).address(r.address())).toList());
    }
}
