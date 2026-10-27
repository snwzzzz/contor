package com.example.contor.service;

import com.example.contor.domain.ChatConversation;
import com.example.contor.domain.ChatMessage;
import com.example.contor.domain.ChatMessageState;
import com.example.contor.domain.ChatRole;
import com.example.contor.domain.ReportBlockKind;
import com.example.contor.persistence.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The stub answerer of H6, until the plan/retrieve/answer pipeline (H3–H5) replaces it. */
@ExtendWith(MockitoExtension.class)
class ChatAnswererTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatAnswerer chatAnswerer;

    @Test
    void answer_WritesThePlaceholderAnswer() {
        var answer = answer(ChatMessageState.PENDING);
        when(chatMessageRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

        chatAnswerer.answer(8L);

        assertThat(answer.getState()).isEqualTo(ChatMessageState.DONE);
        assertThat(answer.getContent()).isEqualTo(ChatAnswerer.PLACEHOLDER);
        assertThat(answer.getBlocks()).singleElement().satisfies(block -> {
            assertThat(block.kind()).isEqualTo(ReportBlockKind.PARAGRAPH);
            assertThat(block.items()).singleElement().satisfies(item -> assertThat(item.text()).isEqualTo(ChatAnswerer.PLACEHOLDER));
        });
        assertThat(answer.getPromptVersion()).isEqualTo(ChatAnswerer.PROMPT_VERSION);
        verify(chatMessageRepository).saveAndFlush(answer);
    }

    @Test
    void answer_DeletedMessageIgnored() {
        when(chatMessageRepository.findById(new ChatMessage.ChatMessageId(8L))).thenReturn(Optional.empty());

        chatAnswerer.answer(8L);

        verify(chatMessageRepository, never()).saveAndFlush(any());
    }

    @Test
    void answer_FinishedAnswerIgnored() {
        var answer = answer(ChatMessageState.FAILED);
        when(chatMessageRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

        chatAnswerer.answer(8L);

        assertThat(answer.getState()).isEqualTo(ChatMessageState.FAILED);
        verify(chatMessageRepository, never()).saveAndFlush(any());
    }

    private static ChatMessage answer(ChatMessageState state) {
        return ChatMessage.builder().id(new ChatMessage.ChatMessageId(8L)).conversation(new ChatConversation()).position(1)
                .role(ChatRole.ASSISTANT).state(state).build();
    }
}
