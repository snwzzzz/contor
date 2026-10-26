package com.example.contor.mailprovider;

import com.example.contor.config.EncryptionProperties;
import com.example.contor.domain.MailProvider;
import com.example.contor.security.FieldEncryptor;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OAuthStateCodecTest {

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(new EncryptionProperties(key(), 1));
    private final OAuthStateCodec codec = new OAuthStateCodec(fieldEncryptor);

    private static String key() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) bytes[i] = (byte) (i + 1);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void round_trips_user_provider_and_verifier() {
        var state = codec.start(42L, MailProvider.GMAIL);

        var decoded = codec.decode(codec.encode(state));

        assertThat(decoded).isEqualTo(state);
        assertThat(decoded.userId()).isEqualTo(42L);
        assertThat(decoded.provider()).isEqualTo(MailProvider.GMAIL);
    }

    @Test
    void every_start_gets_a_fresh_pkce_verifier_of_valid_length() {
        var first = codec.start(1L, MailProvider.MICROSOFT_GRAPH);
        var second = codec.start(1L, MailProvider.MICROSOFT_GRAPH);

        assertThat(first.codeVerifier()).isNotEqualTo(second.codeVerifier());
        // RFC 7636: 43-128 characters of [A-Z a-z 0-9 - . _ ~]
        assertThat(first.codeVerifier()).matches("[A-Za-z0-9._~-]{43,128}");
    }

    @Test
    void code_challenge_is_the_s256_hash_of_the_verifier() {
        // RFC 7636, appendix B
        assertThat(OAuthStateCodec.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void expired_state_is_rejected() {
        var state = codec.start(42L, MailProvider.GMAIL);
        String encoded = codec.encode(state, Instant.now().minusSeconds(1800));

        assertThatThrownBy(() -> codec.decode(encoded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tampered_state_is_rejected() {
        assertThatThrownBy(() -> codec.decode("invalid-state")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missing_state_is_rejected() {
        assertThatThrownBy(() -> codec.decode(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void state_with_an_unknown_provider_is_rejected() {
        String forged = fieldEncryptor.encrypt("42:YAHOO:verifier:nonce:" + Instant.now().toEpochMilli());

        assertThatThrownBy(() -> codec.decode(forged)).isInstanceOf(IllegalArgumentException.class);
    }
}
