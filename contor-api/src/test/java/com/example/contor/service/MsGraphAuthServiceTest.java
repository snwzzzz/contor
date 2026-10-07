package com.example.contor.service;

import com.example.contor.config.EncryptionProperties;
import com.example.contor.config.MsGraphProperties;
import com.example.contor.security.FieldEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MsGraphAuthServiceTest {

    private FieldEncryptor fieldEncryptor;
    private MsGraphProperties properties;
    private MsGraphAuthService authService;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) keyBytes[i] = (byte) (i + 1);
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        fieldEncryptor = new FieldEncryptor(new EncryptionProperties(base64Key, 1));

        properties = new MsGraphProperties(
                "test-client-id",
                "test-secret",
                "common",
                "http://localhost:8080/api/mail/accounts/callback",
                "Mail.Read,User.Read,offline_access"
        );

        authService = new MsGraphAuthService(properties, fieldEncryptor);
    }

    @Test
    void validateState_ValidStateExtractsUserId() {
        String state = authService.generateState(42L);
        Long userId = authService.validateStateAndExtractUserId(state);
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void validateState_ExpiredStateThrows() {
        long past = Instant.now().minusSeconds(1800).toEpochMilli();
        String expiredPayload = "42:nonce:" + past;
        String encryptedState = fieldEncryptor.encrypt(expiredPayload);

        assertThatThrownBy(() -> authService.validateStateAndExtractUserId(encryptedState))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateState_TamperedStateThrows() {
        assertThatThrownBy(() -> authService.validateStateAndExtractUserId("invalid-state"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildAuthorizeUrl_ReturnsUrlContainingParameters() {
        String url = authService.buildAuthorizeUrl(42L, "user@outlook.com");
        assertThat(url).contains("https://login.microsoftonline.com/common/oauth2/v2.0/authorize");
        assertThat(url).contains("client_id=test-client-id");
        assertThat(url).contains("login_hint=user%40outlook.com");
    }

    @Test
    void buildAuthorizeUrl_ThrowsWhenNotConfigured() {
        var unconfigured = new MsGraphAuthService(new MsGraphProperties(null, null, "common", "http://localhost:8080/cb", null), fieldEncryptor);
        assertThatThrownBy(() -> unconfigured.buildAuthorizeUrl(1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }
}
