package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.Role;
import com.example.contor.api.model.SetupCommand;
import com.example.contor.api.model.SetupStatusDto;
import com.example.contor.exception.InvalidSetupTokenException;
import com.example.contor.exception.SetupNotAvailableException;
import com.example.contor.security.SetupState;
import com.example.contor.service.SetupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(SetupController.class)
public class SetupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SetupState setupState;

    @MockitoBean
    private SetupService setupService;

    private String body() {
        return objectMapper.writeValueAsString(new SetupCommand("token", "owner", "owner@example.com", "Correct-Horse-7-Battery"));
    }

    @Test
    void status_is_public_while_setup_is_pending() throws Exception {
        when(setupState.isPending()).thenReturn(true);
        when(setupService.getStatus()).thenReturn(new SetupStatusDto(true));

        mockMvc.perform(get("/api/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true));
    }

    @Test
    void complete_setup_returns_201() throws Exception {
        when(setupState.isPending()).thenReturn(true);
        when(setupService.completeSetup(any())).thenReturn(new CurrentUserDto(1L, "owner", "owner@example.com", Role.OWNER, false));

        mockMvc.perform(post("/api/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void complete_setup_returns_403_for_invalid_token() throws Exception {
        when(setupState.isPending()).thenReturn(true);
        when(setupService.completeSetup(any())).thenThrow(new InvalidSetupTokenException());

        mockMvc.perform(post("/api/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid setup token"));
    }

    @Test
    void setup_is_not_reachable_anonymously_once_a_user_exists() throws Exception {
        when(setupState.isPending()).thenReturn(false);

        mockMvc.perform(post("/api/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/setup"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(setupService);
    }

    @Test
    void setup_returns_404_for_logged_in_users_once_completed() throws Exception {
        when(setupState.isPending()).thenReturn(false);
        when(setupService.completeSetup(any())).thenThrow(new SetupNotAvailableException());

        mockMvc.perform(post("/api/setup").with(TestAuth.owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Setup has already been completed"));
    }
}
