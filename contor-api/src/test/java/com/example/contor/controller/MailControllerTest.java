package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.*;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.exception.MailAccountPhotoNotFoundException;
import com.example.contor.exception.MailCallbackException;
import com.example.contor.service.MailService;
import com.example.contor.service.ImageData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    void mail_account_callback_forwards_the_browser_to_the_web_app() throws Exception {
        when(mailService.handleCallback("state1", "code1", null, null))
                .thenReturn("http://localhost:3000/settings/accounts?connected=true");

        mockMvc.perform(get("/api/mail/accounts/callback")
                        .param("state", "state1")
                        .param("code", "code1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(
                        "<meta http-equiv=\"refresh\" content=\"0;url=http://localhost:3000/settings/accounts?connected=true\">")));
    }

    @Test
    void failed_mail_account_callback_also_forwards_to_the_web_app() throws Exception {
        when(mailService.handleCallback(null, null, "access_denied", null))
                .thenThrow(new MailCallbackException("http://localhost:3000/settings/accounts?mailError=x", null));

        mockMvc.perform(get("/api/mail/accounts/callback").param("error", "access_denied"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("content=\"0;url=http://localhost:3000/settings/accounts?mailError=x\"")));
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
        when(mailService.listEmails(2L, 3L, EmailProcessingState.NEW, "order", EmailSearchField.CONTENT, false, EmailFolder.SENT, 1, 10))
                .thenReturn(new EmailPageDto(List.of(), 1, 10, 0L, 0));

        mockMvc.perform(get("/api/emails").with(TestAuth.user()).param("accountId", "2").param("labelId", "3")
                        .param("processingState", "NEW").param("q", "order").param("field", "CONTENT").param("answered", "false").param("folder", "SENT").param("page", "1").param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void list_emails_rejects_an_unknown_folder() throws Exception {
        mockMvc.perform(get("/api/emails").with(TestAuth.user()).param("folder", "TRASH"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggest_email_senders_returns_200() throws Exception {
        when(mailService.suggestEmailSenders("acm", EmailFolder.SENT)).thenReturn(List.of(
                new EmailSuggestionDto(EmailSuggestionKind.COMPANY, "acme.com", "@acme.com", 4L)));

        mockMvc.perform(get("/api/emails/suggestions").with(TestAuth.user()).param("q", "acm").param("folder", "SENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("COMPANY"))
                .andExpect(jsonPath("$[0].value").value("@acme.com"));
    }

    @Test
    void suggest_email_senders_needs_a_query() throws Exception {
        mockMvc.perform(get("/api/emails/suggestions").with(TestAuth.user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggest_email_senders_requires_login() throws Exception {
        mockMvc.perform(get("/api/emails/suggestions").param("q", "acm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_email_thread_returns_200() throws Exception {
        when(mailService.getEmailThread(9L)).thenReturn(new EmailThreadDto(List.of(
                new EmailDto().id(9L).subject("Shipped"), new EmailDto().id(10L).subject("Re: Shipped").folder("sent")),
                List.of(new ReplyDraftDto().id(4L).body("Thanks").status(ReplyDraftState.READY)), ReplySuggestionState.IDLE)
                .conversationId("conv-1"));

        mockMvc.perform(get("/api/emails/9/thread").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.messages[1].folder").value("sent"))
                .andExpect(jsonPath("$.replyDrafts[0].status").value("READY"))
                .andExpect(jsonPath("$.replySuggestion").value("IDLE"));
    }

    @Test
    void get_email_paragraphs_returns_200() throws Exception {
        when(mailService.getEmailParagraphs(9L)).thenReturn(List.of(
                new EmailParagraphDto(0, "Your order has shipped.", "0123456789abcdef"),
                new EmailParagraphDto(1, "It is held at customs.", "fedcba9876543210")));

        mockMvc.perform(get("/api/emails/9/paragraphs").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].text").value("It is held at customs."))
                .andExpect(jsonPath("$[1].hash").value("fedcba9876543210"));
    }

    @Test
    void get_email_paragraphs_returns_404() throws Exception {
        when(mailService.getEmailParagraphs(9L)).thenThrow(new EmailNotFoundException(9L));

        mockMvc.perform(get("/api/emails/9/paragraphs").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void get_email_paragraphs_requires_login() throws Exception {
        mockMvc.perform(get("/api/emails/9/paragraphs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_email_thread_returns_404() throws Exception {
        when(mailService.getEmailThread(9L)).thenThrow(new EmailNotFoundException(9L));

        mockMvc.perform(get("/api/emails/9/thread").with(TestAuth.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_email_thread_requires_login() throws Exception {
        mockMvc.perform(get("/api/emails/9/thread"))
                .andExpect(status().isUnauthorized());
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

    @Test
    void get_mail_account_photo_returns_the_image() throws Exception {
        when(mailService.getPhoto(2L)).thenReturn(new ImageData(new byte[] {1, 2, 3}, "image/png"));

        mockMvc.perform(get("/api/mail/accounts/2/photo").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void get_mail_account_photo_without_photo_returns_404() throws Exception {
        when(mailService.getPhoto(2L)).thenThrow(new MailAccountPhotoNotFoundException(2L));

        mockMvc.perform(get("/api/mail/accounts/2/photo").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void get_mail_account_photo_requires_login() throws Exception {
        mockMvc.perform(get("/api/mail/accounts/2/photo"))
                .andExpect(status().isUnauthorized());
    }
}
