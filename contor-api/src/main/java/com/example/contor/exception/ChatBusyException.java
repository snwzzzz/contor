package com.example.contor.exception;

/** A new question while the conversation's last answer is still being written (409). */
public class ChatBusyException extends ConflictException {
    public ChatBusyException(Long id) {
        super("Chat conversation " + id + " is still answering");
    }
}
