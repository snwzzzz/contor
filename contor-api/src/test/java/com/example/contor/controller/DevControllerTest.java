package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.DevLoginDto;
import com.example.contor.api.model.Role;
import com.example.contor.service.DevLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@ControllerTest(DevController.class)
public class DevControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DevLoginService devLoginService;

    @Test
    void get_dev_login_is_public_and_names_the_account() throws Exception {
        when(devLoginService.getDevLogin(any())).thenReturn(new DevLoginDto("snwz"));

        mockMvc.perform(get("/api/auth/dev-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("snwz"));
    }

    @Test
    void dev_login_signs_in_without_credentials() throws Exception {
        when(devLoginService.signIn(any(), any())).thenReturn(new CurrentUserDto(1L, "snwz", "snwz@outlook.com", Role.OWNER, false));

        mockMvc.perform(post("/api/auth/dev-login").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("snwz"));
    }

    @Test
    void dev_login_needs_a_csrf_token() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dev_login_from_another_machine_returns_403() throws Exception {
        when(devLoginService.signIn(any(), any())).thenThrow(new AccessDeniedException("Dev login is only available on this machine"));

        mockMvc.perform(post("/api/auth/dev-login").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
