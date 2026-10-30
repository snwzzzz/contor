package com.example.contor.config;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GmailPropertiesTest {

    private static GmailProperties props(String clientId, String clientSecret, String scopes) {
        return new GmailProperties(clientId, clientSecret, "http://localhost:8080/api/mail/accounts/callback", scopes, 365);
    }

    @Test
    void is_configured_with_client_id_and_secret() {
        assertThat(props("id.apps.googleusercontent.com", "secret", null).isConfigured()).isTrue();
        assertThat(props("", "secret", null).isConfigured()).isFalse();
        assertThat(props("id", null, null).isConfigured()).isFalse();
    }

    @Test
    void scope_parameter_is_space_separated_from_commas_or_spaces() {
        assertThat(props("id", "s", "openid, email,https://www.googleapis.com/auth/gmail.readonly").scopeParameter())
                .isEqualTo("openid email https://www.googleapis.com/auth/gmail.readonly");
        assertThat(props("id", "s", "openid  email").scopeParameter()).isEqualTo("openid email");
    }

    @Test
    void default_scopes_read_mail_profile_and_contacts() {
        assertThat(props("id", "s", " ").scopeParameter()).isEqualTo(
                "openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/contacts.readonly");
    }

    @Test
    void initial_sync_days_falls_back_to_a_year_when_not_positive() {
        assertThat(new GmailProperties("id", "s", "http://cb", null, 0).initialSyncDays()).isEqualTo(365);
        assertThat(new GmailProperties("id", "s", "http://cb", null, 30).initialSyncDays()).isEqualTo(30);
    }
}
