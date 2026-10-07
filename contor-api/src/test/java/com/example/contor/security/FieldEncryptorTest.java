package com.example.contor.security;

import com.example.contor.config.EncryptionProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class FieldEncryptorTest {

    public static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final FieldEncryptor encryptor = new FieldEncryptor(new EncryptionProperties(TEST_KEY, 1));

    @Test
    public void encrypts_and_decrypts_round_trip() {
        var cipherText = encryptor.encrypt("refresh-token-123");

        assertThat(encryptor.decrypt(cipherText)).isEqualTo("refresh-token-123");
    }

    @Test
    public void cipher_text_has_key_version_prefix_and_no_plaintext() {
        var cipherText = encryptor.encrypt("refresh-token-123");

        assertThat(cipherText).startsWith("v1:").doesNotContain("refresh-token-123");
    }

    @Test
    public void uses_a_random_iv_per_value() {
        assertThat(encryptor.encrypt("same")).isNotEqualTo(encryptor.encrypt("same"));
    }

    @Test
    public void tampered_cipher_text_is_rejected() {
        var cipherText = encryptor.encrypt("secret");
        var bytes = Base64.getDecoder().decode(cipherText.substring(3));
        bytes[bytes.length - 1] ^= 1;
        var tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void unknown_key_version_is_rejected() {
        var cipherText = encryptor.encrypt("secret").replaceFirst("^v1:", "v2:");

        assertThatThrownBy(() -> encryptor.decrypt(cipherText))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key version");
    }

    @Test
    public void refuses_missing_key() {
        assertThatThrownBy(() -> new FieldEncryptor(new EncryptionProperties(null, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new FieldEncryptor(new EncryptionProperties(" ", 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void refuses_key_that_is_not_base64_or_not_256_bit() {
        assertThatThrownBy(() -> new FieldEncryptor(new EncryptionProperties("not base64!", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");
        var shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new FieldEncryptor(new EncryptionProperties(shortKey, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256");
    }

    @Test
    public void error_messages_never_contain_the_key() {
        var shortKey = Base64.getEncoder().encodeToString("sixteen byte key".getBytes());
        assertThatThrownBy(() -> new FieldEncryptor(new EncryptionProperties(shortKey, 1)))
                .hasMessageNotContaining(shortKey);
    }
}
