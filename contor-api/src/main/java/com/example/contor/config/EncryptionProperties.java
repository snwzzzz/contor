package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Key for {@code *_enc} columns. {@code key} is a base64-encoded 256-bit AES key ({@code APP_ENCRYPTION_KEY}).
 * {@code keyVersion} is written as prefix into every value so keys can be rotated later.
 */
@ConfigurationProperties("app.encryption")
public record EncryptionProperties(String key, @DefaultValue("1") int keyVersion) {
}
