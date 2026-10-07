package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.*;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(MailController.class)
public class MailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailService mailService;

    @Test
    void list_mail_accounts_returns_200() throws Exception {
        when(mailService.listMailAccounts()).thenReturn(List.of(
                new MailAccountDto(2L, MailProviderKind.MICROSOFT_GRAPH, "me@example.com", MailAccountState.ACTIVE)));

        mockMvc.perform(get("/api/mail/accounts").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emailAddress").value("me@example.com"))
                .andExpect(jsonPath("$[0].accessToken").doesNotExist());
    }

    @Test
    void connect_mail_account_owner_returns_200() throws Exception {
        when(mailService.connectAccount(any())).thenReturn(new MailAuthorizeUrlDto("https://login.microsoft.com/auth"));

        mockMvc.perform(post("/api/mail/accounts/connect").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").value("https://login.microsoft.com/auth"));
    }

    @Test
    void connect_mail_account_user_forbidden() throws Exception {
        mockMvc.perform(post("/api/mail/accounts/connect").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mail_account_callback_redirects_302() throws Exception {
        doNothing().when(mailService).handleCallback("state1", "code1", null, null);

        mockMvc.perform(get("/api/mail/accounts/callback")
                        .param("state", "state1")
                        .param("code", "code1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/settings/mail?connected=true"));
    }

    @Test
    void sync_mail_account_owner_returns_200() throws Exception {
        when(mailService.syncAccount(2L)).thenReturn(new MailSyncStatusDto(2L, MailAccountState.ACTIVE).messagesSynced(5));

        mockMvc.perform(post("/api/mail/accounts/2/sync").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(2))
                .andExpect(jsonPath("$.messagesSynced").value(5));
    }

    @Test
    void sync_mail_account_user_forbidden() throws Exception {
        mockMvc.perform(post("/api/mail/accounts/2/sync").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void disconnect_mail_account_returns_204() throws Exception {
        doNothing().when(mailService).disconnectAccount(2L);

        mockMvc.perform(post("/api/mail/accounts/2/disconnect").with(TestAuth.owner()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void get_mail_account_sync_status_returns_200() throws Exception {
        when(mailService.getSyncStatus(2L)).thenReturn(new MailSyncStatusDto(2L, MailAccountState.ACTIVE));

        mockMvc.perform(get("/api/mail/accounts/2/status").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void list_emails_passes_filters() throws Exception {
        when(mailService.listEmails(2L, 3L, EmailProcessingState.NEW, "order", 1, 10))
                .thenReturn(new EmailPageDto(List.of(), 1, 10, 0L, 0));

        mockMvc.perform(get("/api/emails").with(TestAuth.user()).param("accountId", "2").param("labelId", "3")
                        .param("processingState", "NEW").param("q", "order").param("page", "1").param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void get_email_returns_404() throws Exception {
        when(mailService.getEmail(9L)).thenThrow(new EmailNotFoundException(9L));

        mockMvc.perform(get("/api/emails/9").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Email not found with id: 9"));
    }

    @Test
    void mail_is_read_only() throws Exception {
        mockMvc.perform(post("/api/emails").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void get_email_returns_200() throws Exception {
        when(mailService.getEmail(5L)).thenReturn(new EmailDto().id(5L).subject("Shipped"));

        mockMvc.perform(get("/api/emails/5").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Shipped"));
    }
}
