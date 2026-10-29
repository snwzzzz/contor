package com.example.contor.externalapi;

import com.example.contor.domain.ApiHeader;
import com.example.contor.domain.ApiHttpMethod;
import com.example.contor.domain.ApiSecret;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.security.OutboundRequestGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/**
 * Calls one confirmed endpoint of an external API (read-only, ADR 0012): the endpoint's path below the base URL with
 * its fixed query, plain headers, then secret headers, then the credentials of the app's {@link ApiAuthHandler} (a
 * later header of the same name wins). A POST read query sends the endpoint's body as JSON. Everything goes through the
 * {@link OutboundRequestGuard}; its exceptions, and the handler's {@link ApiAuthException}, reach the caller.
 * Entities may be unsaved (a test call from the modal).
 */
@RequiredArgsConstructor
@Component
public class ExternalApiClient {

    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final int MAX_BYTES = 1024 * 1024;
    private static final String ACCEPT = "application/json, text/plain;q=0.5, */*;q=0.1";
    private static final String USER_AGENT = "contor/1.0 (external API)";

    private final OutboundRequestGuard guard;
    private final ApiAuthRegistry authRegistry;
    private final ApiSecretCodec secretCodec;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * @throws IllegalArgumentException when the app lacks settings or credentials its auth kind needs (nothing is sent)
     * @throws IllegalStateException    when the stored credentials cannot be read (nothing is sent)
     */
    public ApiResponse call(ExternalApi api, ExternalApiEndpoint endpoint) {
        ApiSecret secret = secretCodec.read(api);
        ApiAuthHandler auth = authRegistry.handler(api.getAuthKind());
        auth.validate(api.getAuthConfig(), secret);

        ApiRequest request = new ApiRequest();
        request.header("Accept", ACCEPT);
        request.header("User-Agent", USER_AGENT);
        for (ApiHeader header : api.getHeaders()) {
            request.header(header.name(), header.value());
        }
        for (ApiHeader header : secret.headers()) {
            request.header(header.name(), header.value());
        }
        endpoint.getQuery().forEach(request::queryParam);
        auth.apply(api.getAuthConfig(), secret, request);

        URI uri = request.uri(api.getBaseUrl().resolve(endpoint.getPath()));
        boolean post = endpoint.getMethod() == ApiHttpMethod.POST;
        byte[] body = post ? objectMapper.writeValueAsBytes(endpoint.getBody() != null ? endpoint.getBody() : Map.of()) : null;
        OutboundRequestGuard.Response response = guard.send(new OutboundRequestGuard.Request(endpoint.getMethod().name(), uri,
                request.headers(), body, post ? "application/json" : null, TIMEOUT, MAX_BYTES));
        return new ApiResponse(response.status(), response.contentType(),
                new String(response.body(), charset(response.contentType())), response.truncated(),
                response.header("Retry-After").map(this::retryAfter).orElse(null));
    }

    private static Charset charset(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String trimmed = part.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                    try {
                        return Charset.forName(trimmed.substring("charset=".length()).replace("\"", ""));
                    } catch (IllegalArgumentException e) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** Seconds or an HTTP date (RFC 9110); {@code null} when it is neither. */
    private Duration retryAfter(String value) {
        String trimmed = value.trim();
        if (trimmed.matches("\\d{1,9}")) {
            return Duration.ofSeconds(Long.parseLong(trimmed));
        }
        try {
            Duration wait = Duration.between(clock.instant(), ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            return wait.isNegative() ? Duration.ZERO : wait;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
