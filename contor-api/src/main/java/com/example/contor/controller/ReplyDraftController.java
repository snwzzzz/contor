package com.example.contor.controller;

import com.example.contor.api.ReplyDraftsApi;
import com.example.contor.api.model.ReplyDraftDto;
import com.example.contor.api.model.UpdateReplyDraftCommand;
import com.example.contor.service.ReplyDraftService;
import com.example.contor.service.ReplySuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ReplyDraftController implements ReplyDraftsApi {

    private final ReplyDraftService replyDraftService;
    private final ReplySuggestionService replySuggestionService;

    @Override
    public ResponseEntity<Void> suggestReply(Long emailId) {
        replySuggestionService.requestSuggestion(emailId);
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<ReplyDraftDto> updateReplyDraft(Long replyDraftId, UpdateReplyDraftCommand updateReplyDraftCommand) {
        return ResponseEntity.ok(replyDraftService.updateReplyDraft(replyDraftId, updateReplyDraftCommand));
    }

    @Override
    public ResponseEntity<ReplyDraftDto> discardReplyDraft(Long replyDraftId) {
        return ResponseEntity.ok(replyDraftService.discardReplyDraft(replyDraftId));
    }
}
