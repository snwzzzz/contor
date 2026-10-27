package com.example.contor.exception;

public class ChatMessageNotFoundException extends ResourceNotFoundException {

    public ChatMessageNotFoundException(Long id) {
        super("Chat message not found with id: " + id);
    }
}
