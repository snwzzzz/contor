package com.example.contor.service;

import com.example.contor.api.model.ReplyDraftDto;
import com.example.contor.api.model.UpdateReplyDraftCommand;
import com.example.contor.domain.ReplyDraft;
import com.example.contor.domain.ReplyDraftStatus;
import com.example.contor.exception.ReplyDraftNotEditableException;
import com.example.contor.exception.ReplyDraftNotFoundException;
import com.example.contor.mapper.ReplyDraftMapper;
import com.example.contor.persistence.ReplyDraftRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The current user's kept reply drafts: edit the text or discard them. Nothing here sends mail. */
@RequiredArgsConstructor
@Service
public class ReplyDraftService {

    private final ReplyDraftRepository replyDraftRepository;

    @Transactional
    public ReplyDraftDto updateReplyDraft(Long id, UpdateReplyDraftCommand command) {
        ReplyDraft draft = load(id);
        if (draft.getStatus() != ReplyDraftStatus.READY) {
            throw new ReplyDraftNotEditableException(id);
        }
        if (command.getToRecipients() != null) {
            if (command.getToRecipients().isEmpty() || command.getToRecipients().stream()
                    .anyMatch(recipient -> recipient.getAddress() == null || recipient.getAddress().isBlank())) {
                throw new IllegalArgumentException("toRecipients needs at least one recipient, each with an address");
            }
            draft.setToRecipients(ReplyDraftMapper.toRecipients(command.getToRecipients()));
        }
        if (command.getCcRecipients() != null) {
            if (command.getCcRecipients().stream().anyMatch(recipient -> recipient.getAddress() == null || recipient.getAddress().isBlank())) {
                throw new IllegalArgumentException("ccRecipients need an address each");
            }
            draft.setCcRecipients(ReplyDraftMapper.toRecipients(command.getCcRecipients()));
        }
        if (command.getSubject() != null) {
            draft.setSubject(command.getSubject().strip());
        }
        if (command.getBody() != null) {
            if (command.getBody().isBlank()) {
                throw new IllegalArgumentException("body must not be empty");
            }
            draft.setBody(command.getBody().strip());
        }
        return ReplyDraftMapper.toDto(replyDraftRepository.saveAndFlush(draft));
    }

    @Transactional
    public ReplyDraftDto discardReplyDraft(Long id) {
        ReplyDraft draft = load(id);
        draft.setStatus(ReplyDraftStatus.DISCARDED);
        return ReplyDraftMapper.toDto(replyDraftRepository.saveAndFlush(draft));
    }

    /** A draft of one of the current user's mailboxes; anything else is "not found". */
    private ReplyDraft load(Long id) {
        Long userId = CurrentActor.userId().orElseThrow(() -> new IllegalStateException("No authenticated user"));
        return replyDraftRepository.findById(new ReplyDraft.ReplyDraftId(id))
                .filter(draft -> draft.getAccount().getOwner().getId().id().equals(userId))
                .orElseThrow(() -> new ReplyDraftNotFoundException(id));
    }
}
