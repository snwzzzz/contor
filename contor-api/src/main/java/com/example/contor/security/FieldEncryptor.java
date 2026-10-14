package com.example.contor.security;

import com.example.contor.config.EncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for secrets stored in {@code *_enc} columns.
 * Format: {@code v<keyVersion>:base64(iv || ciphertext+tag)}, with a random 96-bit IV per value.
 */
public class FieldEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKey key;
    private final String prefix;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(EncryptionProperties properties) {
        this.key = parseKey(properties.key());
        this.prefix = "v" + properties.keyVersion() + ":";
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY is not set (base64-encoded 256-bit key required)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY is not valid base64");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must be a 256-bit key (32 bytes), got " + bytes.length * 8 + " bits");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return prefix + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (!stored.startsWith(prefix)) {
            throw new IllegalStateException("Unsupported key version for encrypted value");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(prefix.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Decryption failed");
        }
    }
}
