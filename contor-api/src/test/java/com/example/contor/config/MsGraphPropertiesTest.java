package com.example.contor.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MsGraphPropertiesTest {

    @Test
    void isConfigured_ReturnsTrueWhenClientIdAndSecretPresent() {
        var props = new MsGraphProperties("my-client-id", "my-secret", "common", "http://localhost:8080/cb", "Mail.Read");
        assertThat(props.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_ReturnsFalseWhenMissing() {
        var props = new MsGraphProperties("", null, "common", "http://localhost:8080/cb", "Mail.Read");
        assertThat(props.isConfigured()).isFalse();
    }

    @Test
    void scopeSet_ParsesCommaSeparatedScopes() {
        var props = new MsGraphProperties("id", "sec", "common", "http://localhost:8080/cb", "Mail.Read, User.Read, offline_access");
        assertThat(props.scopeSet()).containsExactlyInAnyOrder("Mail.Read", "User.Read", "offline_access");
    }

    @Test
    void scopeParameter_IsSpaceSeparatedAsOAuthRequires() {
        var props = new MsGraphProperties("id", "sec", "common", "http://localhost:8080/cb", "Mail.Read, User.Read,offline_access");
        assertThat(props.scopeParameter()).isEqualTo("Mail.Read User.Read offline_access");
    }

    @Test
    void scopeParameter_AcceptsSpaceSeparatedInput() {
        var props = new MsGraphProperties("id", "sec", "common", "http://localhost:8080/cb", "Mail.Read User.Read offline_access");
        assertThat(props.scopeParameter()).isEqualTo("Mail.Read User.Read offline_access");
    }

    @Test
    void authority_FormatsMicrosoftEndpoint() {
        var props = new MsGraphProperties("id", "sec", "my-tenant", "http://localhost:8080/cb", "Mail.Read");
        assertThat(props.authority()).isEqualTo("https://login.microsoftonline.com/my-tenant/");
    }
}
