package com.example.contor.exception;

public class ReplyDraftNotFoundException extends ResourceNotFoundException {

    public ReplyDraftNotFoundException(Long id) {
        super("Reply draft not found with id: " + id);
    }
}
