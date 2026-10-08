package com.example.contor.service;

import com.example.contor.config.MsGraphProperties;
import com.example.contor.security.FieldEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class MsGraphAuthService {

    private static final Duration STATE_TTL = Duration.ofMinutes(15);

    private final MsGraphProperties properties;
    private final FieldEncryptor fieldEncryptor;
    private final RestClient restClient;

    public MsGraphAuthService(MsGraphProperties properties, FieldEncryptor fieldEncryptor) {
        this.properties = properties;
        this.fieldEncryptor = fieldEncryptor;
        this.restClient = RestClient.builder().build();
    }

    public record TokenResult(String accessToken, String refreshToken, Instant expiresAt) {}

    public record MicrosoftTokenResponse(
            String token_type,
            String scope,
            Long expires_in,
            String access_token,
            String refresh_token,
            String id_token,
            String error,
            String error_description) {}

    public String generateState(Long userId) {
        String payload = userId + ":" + UUID.randomUUID() + ":" + Instant.now().toEpochMilli();
        return fieldEncryptor.encrypt(payload);
    }

    public Long validateStateAndExtractUserId(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state is missing");
        }
        String decrypted;
        try {
            decrypted = fieldEncryptor.decrypt(state);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid OAuth state");
        }
        String[] parts = decrypted.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed OAuth state");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed OAuth state timestamp");
        }
        if (Instant.ofEpochMilli(timestamp).plus(STATE_TTL).isBefore(Instant.now())) {
            throw new IllegalArgumentException("OAuth state has expired");
        }
        try {
            return Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed OAuth state user ID");
        }
    }

    public String buildAuthorizeUrl(Long userId, String emailHint) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Microsoft Graph is not configured (MSGRAPH_CLIENT_ID and MSGRAPH_CLIENT_SECRET required)");
        }
        String state = generateState(userId);
        StringBuilder sb = new StringBuilder(properties.authorizeEndpoint())
                .append("?client_id=").append(URLEncoder.encode(properties.clientId(), StandardCharsets.UTF_8))
                .append("&response_type=code")
                .append("&redirect_uri=").append(URLEncoder.encode(properties.redirectUri(), StandardCharsets.UTF_8))
                .append("&response_mode=query")
                .append("&scope=").append(URLEncoder.encode(properties.scopeParameter(), StandardCharsets.UTF_8))
                .append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        if (emailHint != null && !emailHint.isBlank()) {
            sb.append("&login_hint=").append(URLEncoder.encode(emailHint, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public TokenResult exchangeCode(String code) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Microsoft Graph is not configured");
        }
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", properties.clientId());
        formData.add("client_secret", properties.clientSecret());
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("redirect_uri", properties.redirectUri());
        formData.add("scope", properties.scopeParameter());

        return executeTokenRequest(formData);
    }

    public TokenResult refreshToken(String refreshToken) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Microsoft Graph is not configured");
        }
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", properties.clientId());
        formData.add("client_secret", properties.clientSecret());
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        formData.add("scope", properties.scopeParameter());

        return executeTokenRequest(formData);
    }

    private TokenResult executeTokenRequest(MultiValueMap<String, String> formData) {
        MicrosoftTokenResponse response = restClient.post()
                .uri(properties.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(MicrosoftTokenResponse.class);

        if (response == null || response.access_token() == null) {
            throw new IllegalStateException("No access token returned from Microsoft Entra ID: " +
                    (response != null ? response.error_description() : "null"));
        }
        Instant expiresAt = response.expires_in() != null
                ? Instant.now().plusSeconds(response.expires_in())
                : Instant.now().plusSeconds(3600);
        return new TokenResult(response.access_token(), response.refresh_token(), expiresAt);
    }
}
