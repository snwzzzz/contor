package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiHeader;
import com.example.contor.domain.ApiHttpMethod;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.Slug;
import com.example.contor.security.OutboundRequestGuard;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExternalApiClientTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private final OutboundRequestGuard guard = mock(OutboundRequestGuard.class);
    private final ExternalApiClient client = new ExternalApiClient(guard, ExternalApiFixtures.authRegistry(),
            JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void calls_the_endpoint_below_the_base_url_with_its_query() {
        answer(200, Map.of("content-type", "application/json"), "[]");
        var endpoint = endpoint("/orders");
        endpoint.setQuery(Map.of("status", "open"));

        client.call(api(), endpoint);

        var request = sent();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri()).isEqualTo(URI.create("https://api.shop.example/v1/orders?status=open"));
        assertThat(request.body()).isNull();
        assertThat(request.timeout()).isEqualTo(ExternalApiClient.TIMEOUT);
        assertThat(request.maxBytes()).isEqualTo(ExternalApiClient.MAX_BYTES);
        assertThat(request.headers()).containsEntry("User-Agent", "contor/1.0 (external API)")
                .containsKey("Accept");
    }

    @Test
    void sends_plain_and_secret_headers_and_the_credentials() {
        answer(200, Map.of(), "");
        var api = api();
        api.setHeaders(List.of(new ApiHeader("X-Shop-Version", "2024-10")));
        api.setAuthKind(ApiAuthKind.API_KEY_QUERY);
        api.setAuthConfig(new ApiAuthConfig(null, "api_key", null, null, null, null));
        api.setSecret("{\"value\":\"key-123\",\"headers\":[{\"name\":\"X-Shop-Token\",\"value\":\"t-1\"}]}");

        client.call(api, endpoint("/orders"));

        var request = sent();
        assertThat(request.uri()).isEqualTo(URI.create("https://api.shop.example/v1/orders?api_key=key-123"));
        assertThat(request.headers()).containsEntry("X-Shop-Version", "2024-10").containsEntry("X-Shop-Token", "t-1");
    }

    @Test
    void credentials_win_over_secret_headers_and_secret_headers_over_plain_ones() {
        answer(200, Map.of(), "");
        var api = api();
        api.setHeaders(List.of(new ApiHeader("authorization", "plain"), new ApiHeader("X-Shop-Token", "plain"),
                new ApiHeader("Accept", "application/xml")));
        api.setAuthKind(ApiAuthKind.BEARER);
        api.setSecret("{\"value\":\"tok-1\",\"headers\":[{\"name\":\"x-shop-token\",\"value\":\"secret\"}]}");

        client.call(api, endpoint("/orders"));

        var headers = sent().headers();
        assertThat(headers.get("Authorization")).isEqualTo("Bearer tok-1");
        assertThat(headers.get("X-Shop-Token")).isEqualTo("secret");
        assertThat(headers.get("Accept")).isEqualTo("application/xml");
    }

    @Test
    void posts_the_body_of_a_read_query_as_json() {
        answer(200, Map.of(), "{}");
        var endpoint = endpoint("/graphql");
        endpoint.setMethod(ApiHttpMethod.POST);
        endpoint.setBody(Map.of("query", "{ orders { id } }"));

        client.call(api(), endpoint);

        var request = sent();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.contentType()).isEqualTo("application/json");
        assertThat(new String(request.body(), StandardCharsets.UTF_8)).isEqualTo("{\"query\":\"{ orders { id } }\"}");
    }

    @Test
    void returns_status_type_body_and_retry_after() {
        answer(429, Map.of("content-type", "application/json; charset=utf-8", "retry-after", "120"), "{\"error\":\"slow down\"}");

        var response = client.call(api(), endpoint("/orders"));

        assertThat(response.status()).isEqualTo(429);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.contentType()).isEqualTo("application/json; charset=utf-8");
        assertThat(response.body()).isEqualTo("{\"error\":\"slow down\"}");
        assertThat(response.truncated()).isFalse();
        assertThat(response.retryAfter()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void reads_retry_after_as_http_date_too() {
        answer(503, Map.of("retry-after", "Thu, 29 Oct 2026 10:05:00 GMT"), "");

        assertThat(client.call(api(), endpoint("/orders")).retryAfter()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void ignores_a_retry_after_it_cannot_read() {
        answer(503, Map.of("retry-after", "soon"), "");

        assertThat(client.call(api(), endpoint("/orders")).retryAfter()).isNull();
    }

    @Test
    void decodes_the_body_with_the_charset_of_the_answer() {
        when(guard.send(any())).thenReturn(new OutboundRequestGuard.Response(200, Map.of("content-type", "text/plain; charset=ISO-8859-1"),
                "Grüße".getBytes(StandardCharsets.ISO_8859_1), false));

        var response = client.call(api(), endpoint("/orders"));

        assertThat(response.body()).isEqualTo("Grüße");
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void refuses_to_call_without_the_credentials_it_needs() {
        var api = api();
        api.setAuthKind(ApiAuthKind.BEARER);

        assertThatThrownBy(() -> client.call(api, endpoint("/orders"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
        verifyNoInteractions(guard);
    }

    @Test
    void unreadable_stored_credentials_fail_without_showing_them() {
        var api = api();
        api.setAuthKind(ApiAuthKind.BEARER);
        api.setSecret("{not json key-123");

        assertThatThrownBy(() -> client.call(api, endpoint("/orders"))).isInstanceOf(IllegalStateException.class)
                .message().contains("Shop").doesNotContain("key-123");
        verifyNoInteractions(guard);
    }

    private void answer(int status, Map<String, String> headers, String body) {
        when(guard.send(any())).thenReturn(new OutboundRequestGuard.Response(status, headers, body.getBytes(StandardCharsets.UTF_8), false));
    }

    private OutboundRequestGuard.Request sent() {
        ArgumentCaptor<OutboundRequestGuard.Request> request = ArgumentCaptor.forClass(OutboundRequestGuard.Request.class);
        verify(guard).send(request.capture());
        return request.getValue();
    }

    private static ExternalApi api() {
        return ExternalApi.builder().name("Shop").slug(new Slug("shop")).baseUrl(new BaseUrl("https://api.shop.example/v1")).build();
    }

    private static ExternalApiEndpoint endpoint(String path) {
        return ExternalApiEndpoint.builder().name("orders").path(new ApiPath(path)).build();
    }
}
