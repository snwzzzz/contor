package com.example.contor.exception;

/** Also thrown for another user's conversation: conversations are private. */
public class ChatConversationNotFoundException extends ResourceNotFoundException {

    public ChatConversationNotFoundException(Long id) {
        super("Chat conversation not found with id: " + id);
    }
}
