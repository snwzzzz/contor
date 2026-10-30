package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import com.example.contor.security.OutboundRequestGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAUTH2_CLIENT_CREDENTIALS (RFC 6749 4.4): fetches an access token from {@link ApiAuthConfig#tokenUrl()} with the
 * client ID and secret, through the {@link OutboundRequestGuard}, and sends it as bearer token. Tokens are kept in
 * memory until {@value #EXPIRY_MARGIN_SECONDS} seconds before they expire, per token URL, client, scope and secret (a
 * changed secret gets a new token).
 */
@RequiredArgsConstructor
@Component
public class ClientCredentialsAuthHandler implements ApiAuthHandler {

    private static final long EXPIRY_MARGIN_SECONDS = 60;
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 3600;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BYTES = 64 * 1024;

    private final OutboundRequestGuard guard;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    private record Token(String value, Instant expiresAt) {
    }

    @Override
    public ApiAuthKind kind() {
        return ApiAuthKind.OAUTH2_CLIENT_CREDENTIALS;
    }

    @Override
    public void validate(ApiAuthConfig config, ApiSecret secret) {
        if (config.tokenUrl() == null) throw new IllegalArgumentException("The token URL is missing");
        if (config.clientId() == null) throw new IllegalArgumentException("The client ID is missing");
        if (!secret.hasValue()) throw new IllegalArgumentException("The client secret is missing");
    }

    @Override
    public void apply(ApiAuthConfig config, ApiSecret secret, ApiRequest request) {
        String key = String.join("\n", config.tokenUrl(), config.clientId(), String.valueOf(config.scope()), sha256(secret.value()));
        Instant now = clock.instant();
        Token token = tokens.get(key);
        if (token == null || !now.isBefore(token.expiresAt().minusSeconds(EXPIRY_MARGIN_SECONDS))) {
            token = fetch(config, secret, now);
            tokens.put(key, token);
        }
        request.header("Authorization", "Bearer " + token.value());
    }

    private Token fetch(ApiAuthConfig config, ApiSecret secret, Instant now) {
        URI tokenUrl = URI.create(config.tokenUrl());
        String host = tokenUrl.getHost();
        StringBuilder form = new StringBuilder("grant_type=client_credentials")
                .append("&client_id=").append(encode(config.clientId()))
                .append("&client_secret=").append(encode(secret.value()));
        if (config.scope() != null) {
            form.append("&scope=").append(encode(config.scope()));
        }
        OutboundRequestGuard.Response response = guard.send(new OutboundRequestGuard.Request("POST", tokenUrl,
                Map.of("Accept", "application/json"), form.toString().getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", TIMEOUT, MAX_BYTES));
        int status = response.status();
        if (status == 400 || status == 401 || status == 403) {
            throw new ApiAuthException("The token endpoint " + host + " rejected the client credentials (" + status + ")", true);
        }
        if (status < 200 || status >= 300) {
            throw new ApiAuthException("The token endpoint " + host + " answered " + status, false);
        }
        JsonNode json;
        try {
            json = objectMapper.readTree(response.body());
        } catch (JacksonException e) {
            throw new ApiAuthException("The token endpoint " + host + " did not answer with JSON", false);
        }
        JsonNode accessToken = json.get("access_token");
        JsonNode tokenType = json.get("token_type");
        if (accessToken == null || !accessToken.isString() || accessToken.asString().isBlank()) {
            throw new ApiAuthException("The token endpoint " + host + " sent no access token", false);
        }
        if (tokenType != null && !"bearer".equalsIgnoreCase(tokenType.asString())) {
            throw new ApiAuthException("The token endpoint " + host + " sent a token that is not a bearer token", false);
        }
        JsonNode expiresIn = json.get("expires_in");
        long seconds = expiresIn != null && expiresIn.isNumber() ? Math.max(0, expiresIn.asLong()) : DEFAULT_EXPIRES_IN_SECONDS;
        return new Token(accessToken.asString(), now.plusSeconds(seconds));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
