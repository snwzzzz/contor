package com.example.contor.controller;

import com.example.contor.api.ChatApi;
import com.example.contor.api.model.ChatConversationDto;
import com.example.contor.api.model.ChatConversationSummaryDto;
import com.example.contor.api.model.NewChatMessageCommand;
import com.example.contor.api.model.UpdateChatConversationCommand;
import com.example.contor.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class ChatController implements ChatApi {

    private final ChatService chatService;

    @Override
    public ResponseEntity<List<ChatConversationSummaryDto>> listChatConversations() {
        return ResponseEntity.ok(chatService.findAll());
    }

    @Override
    public ResponseEntity<ChatConversationDto> createChatConversation(NewChatMessageCommand newChatMessageCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createConversation(newChatMessageCommand));
    }

    @Override
    public ResponseEntity<ChatConversationDto> getChatConversation(Long conversationId) {
        return ResponseEntity.ok(chatService.findById(conversationId));
    }

    @Override
    public ResponseEntity<ChatConversationDto> askChatQuestion(Long conversationId, NewChatMessageCommand newChatMessageCommand) {
        return ResponseEntity.accepted().body(chatService.askQuestion(conversationId, newChatMessageCommand));
    }

    @Override
    public ResponseEntity<ChatConversationDto> retryChatMessage(Long conversationId, Long messageId) {
        return ResponseEntity.accepted().body(chatService.retryAnswer(conversationId, messageId));
    }

    @Override
    public ResponseEntity<ChatConversationDto> updateChatConversation(Long conversationId, UpdateChatConversationCommand updateChatConversationCommand) {
        return ResponseEntity.ok(chatService.updateConversation(conversationId, updateChatConversationCommand));
    }

    @Override
    public ResponseEntity<Void> deleteChatConversation(Long conversationId) {
        chatService.deleteConversation(conversationId);
        return ResponseEntity.noContent().build();
    }
}
