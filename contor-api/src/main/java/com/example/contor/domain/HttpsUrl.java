package com.example.contor.domain;

import java.net.URI;
import java.net.URISyntaxException;

/** An HTTPS URL on the default port without user info, e.g. an external API's documentation page. */
public record HttpsUrl(String value) {

    private static final int MAX_LENGTH = 2000;

    public HttpsUrl {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("URL cannot be null or empty");
        if (value.length() > MAX_LENGTH) throw new IllegalArgumentException("URL is too long");
        parse(value);
    }

    /** Parses an HTTPS URL on the default port without user info. */
    static URI parse(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL is not valid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("URL must be an https URL with a host");
        if (uri.getRawUserInfo() != null) throw new IllegalArgumentException("URL cannot contain user info");
        if (uri.getPort() != -1 && uri.getPort() != 443) throw new IllegalArgumentException("URL must use port 443");
        return uri;
    }
}
