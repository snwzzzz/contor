package com.example.contor.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Base URL of an external API: HTTPS on the default port, no user info, query or fragment; lower-case host, no trailing
 * slash. Endpoints are {@link ApiPath}s below it. Whether the host is public is checked on every call
 * ({@code OutboundRequestGuard}), not here: DNS answers change.
 */
public record BaseUrl(String value) {

    private static final int MAX_LENGTH = 2000;

    public BaseUrl {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("base URL cannot be null or empty");
        if (value.length() > MAX_LENGTH) throw new IllegalArgumentException("base URL is too long");
        URI uri = HttpsUrl.parse(value);
        if (uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("base URL cannot have a query or fragment");
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        value = "https://" + uri.getHost().toLowerCase(Locale.ROOT) + port + path;
        try {
            new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("base URL is not valid");
        }
    }

    /** The URL of {@code path} below this base ({@code https://api.example/v1} + {@code /orders}). */
    public URI resolve(ApiPath path) {
        return URI.create(value + path.value());
    }
}
