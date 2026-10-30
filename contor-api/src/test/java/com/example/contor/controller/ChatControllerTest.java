package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.ChatConversationDto;
import com.example.contor.api.model.ChatConversationSummaryDto;
import com.example.contor.api.model.ChatMentionDto;
import com.example.contor.api.model.ChatMentionType;
import com.example.contor.api.model.ChatMessageDto;
import com.example.contor.api.model.ChatMessageRole;
import com.example.contor.api.model.ChatMessageStatus;
import com.example.contor.api.model.NewChatMessageCommand;
import com.example.contor.api.model.UpdateChatConversationCommand;
import com.example.contor.exception.AiUnavailableException;
import com.example.contor.exception.ChatBusyException;
import com.example.contor.exception.ChatConversationNotFoundException;
import com.example.contor.exception.ChatRetryException;
import com.example.contor.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(ChatController.class)
public class ChatControllerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 26, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void list_conversations_returns_200() throws Exception {
        when(chatService.findAll()).thenReturn(List.of(new ChatConversationSummaryDto(5L, "Who is Anna?", true, NOW)));

        mockMvc.perform(get("/api/chat/conversations").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].title").value("Who is Anna?"))
                .andExpect(jsonPath("$[0].answering").value(true));
    }

    @Test
    void list_conversations_without_login_returns_401() throws Exception {
        mockMvc.perform(get("/api/chat/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void create_conversation_returns_201_with_the_pending_answer() throws Exception {
        when(chatService.createConversation(any(NewChatMessageCommand.class))).thenReturn(conversation(ChatMessageStatus.PENDING));

        mockMvc.perform(post("/api/chat/conversations").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Who is Anna?\", \"recordRefs\": [{\"collectionId\": 3, \"recordId\": 4711}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].status").value("PENDING"));
        verify(chatService).createConversation(argThat(command -> command.getText().equals("Who is Anna?")
                && command.getRecordRefs().getFirst().getRecordId() == 4711L));
    }

    @Test
    void create_conversation_without_text_returns_400() throws Exception {
        mockMvc.perform(post("/api/chat/conversations").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(chatService, never()).createConversation(any());
    }

    @Test
    void create_conversation_when_the_ai_is_not_ready_returns_409() throws Exception {
        when(chatService.createConversation(any())).thenThrow(new AiUnavailableException("The AI model is not ready"));

        mockMvc.perform(post("/api/chat/conversations").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Who is Anna?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The AI model is not ready"));
    }

    @Test
    void create_conversation_without_csrf_returns_403() throws Exception {
        mockMvc.perform(post("/api/chat/conversations").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Who is Anna?\"}"))
                .andExpect(status().isForbidden());
        verify(chatService, never()).createConversation(any());
    }

    @Test
    void get_conversation_returns_200_with_the_answer() throws Exception {
        when(chatService.findById(5L)).thenReturn(conversation(ChatMessageStatus.DONE));

        mockMvc.perform(get("/api/chat/conversations/5").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Who is Anna?"))
                .andExpect(jsonPath("$.messages[1].text").value("Anna works at Kessler GmbH."))
                .andExpect(jsonPath("$.messages[1].mentions[0].type").value("RECORD"))
                .andExpect(jsonPath("$.messages[1].mentions[0].recordId").value(4711));
    }

    @Test
    void get_unknown_conversation_returns_404() throws Exception {
        when(chatService.findById(5L)).thenThrow(new ChatConversationNotFoundException(5L));

        mockMvc.perform(get("/api/chat/conversations/5").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void ask_question_returns_202() throws Exception {
        when(chatService.askQuestion(eq(5L), any(NewChatMessageCommand.class))).thenReturn(conversation(ChatMessageStatus.PENDING));

        mockMvc.perform(post("/api/chat/conversations/5/messages").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"And where does she work?\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messages[1].status").value("PENDING"));
    }

    @Test
    void ask_question_while_answering_returns_409() throws Exception {
        when(chatService.askQuestion(eq(5L), any())).thenThrow(new ChatBusyException(5L));

        mockMvc.perform(post("/api/chat/conversations/5/messages").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"And?\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void retry_answer_returns_202() throws Exception {
        when(chatService.retryAnswer(5L, 8L)).thenReturn(conversation(ChatMessageStatus.PENDING));

        mockMvc.perform(post("/api/chat/conversations/5/messages/8/retry").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isAccepted());
    }

    @Test
    void retry_a_finished_answer_returns_409() throws Exception {
        when(chatService.retryAnswer(5L, 8L)).thenThrow(new ChatRetryException());

        mockMvc.perform(post("/api/chat/conversations/5/messages/8/retry").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void update_conversation_returns_200() throws Exception {
        when(chatService.updateConversation(eq(5L), any(UpdateChatConversationCommand.class))).thenReturn(conversation(ChatMessageStatus.DONE));

        mockMvc.perform(patch("/api/chat/conversations/5").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"Anna\"}"))
                .andExpect(status().isOk());
        verify(chatService).updateConversation(eq(5L), argThat(command -> "Anna".equals(command.getTitle())));
    }

    @Test
    void update_conversation_with_a_blank_title_returns_400() throws Exception {
        when(chatService.updateConversation(eq(5L), any())).thenThrow(new IllegalArgumentException("title: must not be empty"));

        mockMvc.perform(patch("/api/chat/conversations/5").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: must not be empty"));
    }

    @Test
    void delete_conversation_returns_204() throws Exception {
        mockMvc.perform(delete("/api/chat/conversations/5").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(chatService).deleteConversation(5L);
    }

    private static ChatConversationDto conversation(ChatMessageStatus answerStatus) {
        var question = new ChatMessageDto(7L, ChatMessageRole.USER, ChatMessageStatus.DONE, List.of(), List.of(), List.of(), NOW).text("Who is Anna?");
        var answer = new ChatMessageDto(8L, ChatMessageRole.ASSISTANT, answerStatus, List.of(), List.of(),
                answerStatus == ChatMessageStatus.DONE ? List.of(new ChatMentionDto(ChatMentionType.RECORD, "Anna Kessler").collectionId(3L).recordId(4711L)) : List.of(),
                NOW);
        if (answerStatus == ChatMessageStatus.DONE) {
            answer.text("Anna works at Kessler GmbH.");
        }
        return new ChatConversationDto(5L, "Who is Anna?", List.of(question, answer), NOW);
    }
}
