package com.example.contor.mailprovider;

import com.example.contor.domain.MailProvider;
import com.example.contor.security.FieldEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * The OAuth {@code state} of a mailbox sign-in: who started it, for which provider, and the PKCE verifier, encrypted
 * with the field key so it cannot be forged or read on its way through the browser. Valid for {@link #TTL}.
 */
@RequiredArgsConstructor
@Component
public class OAuthStateCodec {

    static final Duration TTL = Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final FieldEncryptor fieldEncryptor;

    public record OAuthState(Long userId, MailProvider provider, String codeVerifier) {}

    /** A new sign-in with a fresh PKCE verifier (32 random bytes, RFC 7636). */
    public OAuthState start(Long userId, MailProvider provider) {
        byte[] verifier = new byte[32];
        RANDOM.nextBytes(verifier);
        return new OAuthState(userId, provider, BASE64_URL.encodeToString(verifier));
    }

    public String encode(OAuthState state) {
        return encode(state, Instant.now());
    }

    String encode(OAuthState state, Instant issuedAt) {
        return fieldEncryptor.encrypt(state.userId() + ":" + state.provider().name() + ":" + state.codeVerifier()
                + ":" + UUID.randomUUID() + ":" + issuedAt.toEpochMilli());
    }

    /** @throws IllegalArgumentException when the state is missing, forged, malformed or expired */
    public OAuthState decode(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state is missing");
        }
        String decrypted;
        try {
            decrypted = fieldEncryptor.decrypt(state);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid OAuth state");
        }
        String[] parts = decrypted.split(":");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Malformed OAuth state");
        }
        long issuedAt;
        long userId;
        try {
            issuedAt = Long.parseLong(parts[4]);
            userId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed OAuth state");
        }
        if (Instant.ofEpochMilli(issuedAt).plus(TTL).isBefore(Instant.now())) {
            throw new IllegalArgumentException("OAuth state has expired");
        }
        // valueOf throws IllegalArgumentException for an unknown provider
        return new OAuthState(userId, MailProvider.valueOf(parts[1]), parts[2]);
    }

    /** PKCE S256 challenge of a verifier: base64url(SHA-256(verifier)) without padding. */
    public static String codeChallenge(String codeVerifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
