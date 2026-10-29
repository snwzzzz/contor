package com.example.contor.externalapi;

import com.example.contor.domain.ApiSecret;
import com.example.contor.domain.ExternalApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link ApiSecret} to and from the JSON in {@link ExternalApi#getSecret()} (the column itself is encrypted by its
 * converter). An empty secret is stored as nothing. Errors never show the stored text.
 */
@RequiredArgsConstructor
@Component
public class ApiSecretCodec {

    private final ObjectMapper objectMapper;

    /** {@link ApiSecret#EMPTY} when nothing is stored. */
    public ApiSecret read(ExternalApi api) {
        String json = api.getSecret();
        if (json == null || json.isBlank()) {
            return ApiSecret.EMPTY;
        }
        try {
            return objectMapper.readValue(json, ApiSecret.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new IllegalStateException("The stored credentials of " + api.getName() + " cannot be read");
        }
    }

    /** {@code null} for a secret without value and headers. */
    public String write(ApiSecret secret) {
        if (!secret.hasValue() && secret.headers().isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(new ApiSecret(secret.hasValue() ? secret.value() : null, secret.headers()));
    }
}
