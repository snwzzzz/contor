package com.example.contor.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/** One-time setup token (256 bit, in memory only). Compared in constant time; consumable exactly once. */
@Component
public class SetupTokenHolder {

    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<String> token = new AtomicReference<>();

    public String issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String issued = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        token.set(issued);
        return issued;
    }

    public boolean matches(String candidate) {
        String current = token.get();
        return current != null && candidate != null
                && MessageDigest.isEqual(current.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    /** Atomically invalidates the token if it matches; only one caller can ever succeed. */
    public boolean consume(String candidate) {
        String current = token.get();
        return matches(candidate) && token.compareAndSet(current, null);
    }
}
