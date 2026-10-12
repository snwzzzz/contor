package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.ReplyDraftDto;
import com.example.contor.api.model.ReplyDraftState;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.exception.ReplyDraftNotEditableException;
import com.example.contor.exception.ReplyDraftNotFoundException;
import com.example.contor.exception.ReplySuggestionUnavailableException;
import com.example.contor.service.ReplyDraftService;
import com.example.contor.service.ReplySuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(ReplyDraftController.class)
class ReplyDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReplyDraftService replyDraftService;

    @MockitoBean
    private ReplySuggestionService replySuggestionService;

    private static ReplyDraftDto draft(ReplyDraftState state) {
        return new ReplyDraftDto().id(4L).emailId(8L).accountId(3L).subject("Re: Termin").body("Gerne, am Freitag").status(state);
    }

    @Test
    void any_user_edits_a_draft_of_their_mailbox() throws Exception {
        when(replyDraftService.updateReplyDraft(eq(4L), argThat(c -> "Gerne, am Freitag".equals(c.getBody()))))
                .thenReturn(draft(ReplyDraftState.READY));

        mockMvc.perform(patch("/api/reply-drafts/4").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"Gerne, am Freitag\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Gerne, am Freitag"))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void editing_validates_the_input_and_needs_csrf() throws Exception {
        mockMvc.perform(patch("/api/reply-drafts/4").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/reply-drafts/4").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(replyDraftService);
    }

    @Test
    void foreign_or_discarded_drafts_are_404_and_409() throws Exception {
        when(replyDraftService.updateReplyDraft(eq(4L), argThat(c -> "x".equals(c.getBody())))).thenThrow(new ReplyDraftNotFoundException(4L));
        when(replyDraftService.updateReplyDraft(eq(5L), argThat(c -> "x".equals(c.getBody())))).thenThrow(new ReplyDraftNotEditableException(5L));

        mockMvc.perform(patch("/api/reply-drafts/4").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/reply-drafts/5").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void discards_a_draft() throws Exception {
        when(replyDraftService.discardReplyDraft(4L)).thenReturn(draft(ReplyDraftState.DISCARDED));

        mockMvc.perform(post("/api/reply-drafts/4/discard").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
    }

    @Test
    void suggesting_a_reply_is_accepted() throws Exception {
        mockMvc.perform(post("/api/emails/8/reply-suggestion").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isAccepted());

        verify(replySuggestionService).requestSuggestion(8L);
    }

    @Test
    void suggesting_a_reply_reports_not_found_and_turned_off() throws Exception {
        doThrow(new EmailNotFoundException(8L)).when(replySuggestionService).requestSuggestion(8L);
        doThrow(new ReplySuggestionUnavailableException("Reply drafts are turned off")).when(replySuggestionService).requestSuggestion(9L);

        mockMvc.perform(post("/api/emails/8/reply-suggestion").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/emails/9/reply-suggestion").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reply drafts are turned off"));
    }

    @Test
    void everything_requires_a_session() throws Exception {
        mockMvc.perform(post("/api/emails/8/reply-suggestion").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reply-drafts/4/discard").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(replyDraftService, replySuggestionService);
    }
}
