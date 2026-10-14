package com.example.contor.exception;

/** Only READY reply drafts can be edited (409). */
public class ReplyDraftNotEditableException extends ConflictException {
    public ReplyDraftNotEditableException(Long id) {
        super("Reply draft " + id + " was discarded and cannot be edited");
    }
}
