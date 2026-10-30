package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import com.example.contor.security.OutboundRequestException;
import com.example.contor.security.OutboundRequestGuard;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClientCredentialsAuthHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final ApiAuthConfig CONFIG = new ApiAuthConfig(null, null, "https://auth.shop.example/oauth/token",
            "orders.read", null, "contor-client");
    private static final ApiSecret SECRET = new ApiSecret("client-secret-1", List.of());

    private final OutboundRequestGuard guard = mock(OutboundRequestGuard.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final ClientCredentialsAuthHandler handler = new ClientCredentialsAuthHandler(guard, JsonMapper.builder().build(),
            new Clock() {
                @Override
                public ZoneOffset getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now.get();
                }
            });

    @Test
    void handles_client_credentials() {
        assertThat(handler.kind()).isEqualTo(ApiAuthKind.OAUTH2_CLIENT_CREDENTIALS);
    }

    @Test
    void fetches_a_token_and_sends_it_as_bearer() {
        when(guard.send(any())).thenReturn(token(200, "{\"access_token\":\"at-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        var request = new ApiRequest();

        handler.apply(CONFIG, SECRET, request);

        assertThat(request.headers()).containsEntry("Authorization", "Bearer at-1");
        ArgumentCaptor<OutboundRequestGuard.Request> sent = ArgumentCaptor.forClass(OutboundRequestGuard.Request.class);
        verify(guard).send(sent.capture());
        assertThat(sent.getValue().method()).isEqualTo("POST");
        assertThat(sent.getValue().uri()).isEqualTo(URI.create("https://auth.shop.example/oauth/token"));
        assertThat(sent.getValue().contentType()).isEqualTo("application/x-www-form-urlencoded");
        assertThat(new String(sent.getValue().body(), StandardCharsets.UTF_8))
                .isEqualTo("grant_type=client_credentials&client_id=contor-client&client_secret=client-secret-1&scope=orders.read");
    }

    @Test
    void reuses_the_token_until_shortly_before_it_expires() {
        when(guard.send(any()))
                .thenReturn(token(200, "{\"access_token\":\"at-1\",\"expires_in\":600}"))
                .thenReturn(token(200, "{\"access_token\":\"at-2\",\"expires_in\":600}"));

        handler.apply(CONFIG, SECRET, new ApiRequest());
        now.set(NOW.plusSeconds(500));
        var cached = new ApiRequest();
        handler.apply(CONFIG, SECRET, cached);
        now.set(NOW.plusSeconds(560));
        var renewed = new ApiRequest();
        handler.apply(CONFIG, SECRET, renewed);

        assertThat(cached.headers()).containsEntry("Authorization", "Bearer at-1");
        assertThat(renewed.headers()).containsEntry("Authorization", "Bearer at-2");
        verify(guard, times(2)).send(any());
    }

    @Test
    void a_changed_secret_gets_its_own_token() {
        when(guard.send(any()))
                .thenReturn(token(200, "{\"access_token\":\"at-1\"}"))
                .thenReturn(token(200, "{\"access_token\":\"at-2\"}"));

        handler.apply(CONFIG, SECRET, new ApiRequest());
        var request = new ApiRequest();
        handler.apply(CONFIG, new ApiSecret("client-secret-2", List.of()), request);

        assertThat(request.headers()).containsEntry("Authorization", "Bearer at-2");
    }

    @Test
    void a_rejected_client_is_reported_as_rejected_credentials() {
        when(guard.send(any())).thenReturn(token(401, "{\"error\":\"invalid_client\"}"));

        assertThatThrownBy(() -> handler.apply(CONFIG, SECRET, new ApiRequest()))
                .isInstanceOf(ApiAuthException.class)
                .satisfies(e -> assertThat(((ApiAuthException) e).credentialsRejected()).isTrue())
                .message().contains("auth.shop.example").doesNotContain("client-secret-1");
    }

    @Test
    void server_errors_and_malformed_answers_are_not_rejected_credentials() {
        when(guard.send(any()))
                .thenReturn(token(503, ""))
                .thenReturn(token(200, "<html>"))
                .thenReturn(token(200, "{\"token_type\":\"Bearer\"}"))
                .thenReturn(token(200, "{\"access_token\":\"at\",\"token_type\":\"mac\"}"));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> handler.apply(CONFIG, SECRET, new ApiRequest()))
                    .isInstanceOf(ApiAuthException.class)
                    .satisfies(e -> assertThat(((ApiAuthException) e).credentialsRejected()).isFalse());
        }
    }

    @Test
    void a_refused_token_url_surfaces_as_refused_request() {
        when(guard.send(any())).thenThrow(new OutboundRequestException("auth.shop.example does not resolve to public addresses only"));

        assertThatThrownBy(() -> handler.apply(CONFIG, SECRET, new ApiRequest())).isInstanceOf(OutboundRequestException.class);
    }

    @Test
    void needs_token_url_client_id_and_secret() {
        assertThatThrownBy(() -> handler.validate(new ApiAuthConfig(null, null, null, null, null, "c"), SECRET))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("token URL");
        assertThatThrownBy(() -> handler.validate(new ApiAuthConfig(null, null, "https://auth.shop.example/t", null, null, null), SECRET))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("client ID");
        assertThatThrownBy(() -> handler.validate(CONFIG, ApiSecret.EMPTY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("client secret");
        verifyNoInteractions(guard);
    }

    private static OutboundRequestGuard.Response token(int status, String body) {
        return new OutboundRequestGuard.Response(status, Map.of("content-type", "application/json"),
                body.getBytes(StandardCharsets.UTF_8), false);
    }
}
