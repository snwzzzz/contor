package com.example.contor.exception;

/** A retry of an answer that is not the conversation's failed last message (409). */
public class ChatRetryException extends ConflictException {
    public ChatRetryException() {
        super("Only a failed last answer can be written again");
    }
}
