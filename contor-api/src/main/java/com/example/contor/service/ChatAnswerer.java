package com.example.contor.service;

import com.example.contor.domain.ChatBlock;
import com.example.contor.domain.ChatMessage;
import com.example.contor.domain.ChatMessageState;
import com.example.contor.domain.ReportBlockKind;
import com.example.contor.persistence.ChatMessageRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Writes a chat answer on the AI thread. <b>Stub (plan step H6):</b> answers every question with a placeholder, so the
 * API and the page can be built; the plan/retrieve/answer pipeline (H3–H5) replaces the body of {@link #answer}.
 */
@RequiredArgsConstructor
@Service
public class ChatAnswerer {

    static final String PROMPT_VERSION = "chat-stub";
    static final String PLACEHOLDER = "The assistant cannot answer questions yet. This placeholder answer shows where its answers will appear.";

    private final ChatMessageRepository chatMessageRepository;

    /** Answers the PENDING message {@code messageId}; a deleted or already finished message is left alone. */
    @Transactional
    public void answer(Long messageId) {
        ChatMessage answer = chatMessageRepository.findById(new ChatMessage.ChatMessageId(messageId))
                .filter(message -> message.getState().isActive())
                .orElse(null);
        if (answer == null) {
            return;
        }
        answer.setContent(PLACEHOLDER);
        answer.setBlocks(List.of(new ChatBlock(ReportBlockKind.PARAGRAPH, null, List.of(new ChatBlock.Item(PLACEHOLDER, List.of())), null)));
        answer.setPromptVersion(PROMPT_VERSION);
        answer.setState(ChatMessageState.DONE);
        chatMessageRepository.saveAndFlush(answer);
    }
}
