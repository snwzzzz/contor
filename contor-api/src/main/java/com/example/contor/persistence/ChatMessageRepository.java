package com.example.contor.persistence;

import com.example.contor.domain.ChatConversation;
import com.example.contor.domain.ChatMessage;
import com.example.contor.domain.ChatMessageState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, ChatMessage.ChatMessageId> {

    List<ChatMessage> findByConversation_IdOrderByPositionAsc(ChatConversation.ChatConversationId conversationId);

    /** Messages in one of {@code states} (e.g. answers still being written). */
    List<ChatMessage> findByStateIn(Set<ChatMessageState> states);

    boolean existsByConversation_IdAndStateIn(ChatConversation.ChatConversationId conversationId, Set<ChatMessageState> states);
}
