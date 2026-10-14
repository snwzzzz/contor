package com.example.contor.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LogMaskerTest {

    @Test
    void masks_email_addresses() {
        assertThat(LogMasker.email("john.doe@example.com")).isEqualTo("j***@e***.com");
        assertThat(LogMasker.email("a@b.co.uk")).isEqualTo("a***@b***.uk");
    }

    @Test
    void masks_malformed_and_missing_addresses() {
        assertThat(LogMasker.email("no-at-sign")).isEqualTo("***");
        assertThat(LogMasker.email(null)).isEqualTo("null");
    }

    @Test
    void never_reveals_secrets() {
        assertThat(LogMasker.secret("refresh-token-123")).isEqualTo("[REDACTED]");
        assertThat(LogMasker.secret(null)).isEqualTo("null");
    }

    @Test
    void reduces_free_text_to_its_length() {
        assertThat(LogMasker.text("Dear John, your parcel ...")).isEqualTo("[26 chars]");
    }
}
