package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.ChangePasswordCommand;
import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.LoginCommand;
import com.example.contor.api.model.Role;
import com.example.contor.exception.InvalidCredentialsException;
import com.example.contor.exception.PasswordValidationException;
import com.example.contor.exception.TooManyRequestsException;
import com.example.contor.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ControllerTest(AuthController.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    /** Cookie attributes are asserted over real HTTP in SecurityIntegrationTest: spring-security-test's csrf()
     *  swaps the shared CSRF repository for a session-based one, so cookie checks here would depend on test order. */
    @Test
    void csrf_endpoint_is_public() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent());
    }

    @Test
    void login_returns_200_with_current_user() throws Exception {
        when(authService.login(any(), any(), any())).thenReturn(new CurrentUserDto(1L, "owner", "owner@example.com", Role.OWNER, false));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginCommand("owner", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void login_without_csrf_token_returns_403() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginCommand("owner", "secret"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Invalid or missing CSRF token"));
        verify(authService, never()).login(any(), any(), any());
    }

    @Test
    void login_returns_401_with_generic_message_on_bad_credentials() throws Exception {
        when(authService.login(any(), any(), any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginCommand("owner", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_returns_429_when_rate_limited() throws Exception {
        when(authService.login(any(), any(), any())).thenThrow(new TooManyRequestsException("Too many login attempts. Try again later."));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginCommand("owner", "secret"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void login_returns_400_for_missing_fields() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("password")));
    }

    @Test
    void login_returns_400_for_malformed_json_without_internals() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void me_returns_401_when_anonymous() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void me_returns_current_user() throws Exception {
        when(authService.getCurrentUser()).thenReturn(new CurrentUserDto(1L, "owner", "owner@example.com", Role.OWNER, false));

        mockMvc.perform(get("/api/auth/me").with(TestAuth.owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void logout_returns_204() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(authService).logout(any(), any());
    }

    @Test
    void logout_returns_401_when_anonymous() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void change_password_returns_204() throws Exception {
        mockMvc.perform(put("/api/auth/password").with(TestAuth.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordCommand("old", "Correct-Horse-7-Battery"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void change_password_returns_400_with_all_violations() throws Exception {
        doThrow(new PasswordValidationException(List.of("Password must contain a digit", "Password is too common")))
                .when(authService).changePassword(any(), any());

        mockMvc.perform(put("/api/auth/password").with(TestAuth.owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePasswordCommand("old", "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must contain a digit; Password is too common"));
    }

    @Test
    void unexpected_errors_return_500_without_internals() throws Exception {
        when(authService.getCurrentUser()).thenThrow(new IllegalStateException("db password is hunter2"));

        mockMvc.perform(get("/api/auth/me").with(TestAuth.owner()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void responses_carry_security_headers() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
