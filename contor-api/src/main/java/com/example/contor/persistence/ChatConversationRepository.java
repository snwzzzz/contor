package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, ChatConversation.ChatConversationId> {

    /** The user's conversations, most recently active first. */
    List<ChatConversation> findByOwner_IdOrderByLastModifiedDesc(AppUser.AppUserId ownerId);

    /** A conversation only when it belongs to {@code ownerId} (another user's conversation is not found). */
    Optional<ChatConversation> findByIdAndOwner_Id(ChatConversation.ChatConversationId id, AppUser.AppUserId ownerId);
}
