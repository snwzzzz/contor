package com.example.contor.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A plain (not secret) header sent with every call of an external API. The name is an HTTP token; the value has no
 * control characters, so it cannot add headers. Headers the HTTP client controls are refused. Secret header values are
 * stored encrypted on {@link ExternalApi}, not as this record.
 */
public record ApiHeader(String name, String value) {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_VALUE_LENGTH = 4000;
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$");
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0A-\\x1F\\x7F]");
    private static final Set<String> CLIENT_HEADERS = Set.of("connection", "content-length", "expect", "host", "te",
            "transfer-encoding", "upgrade");

    public ApiHeader {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("header name cannot be null or empty");
        if (name.length() > MAX_NAME_LENGTH || !NAME.matcher(name).matches())
            throw new IllegalArgumentException("header name is not valid");
        if (CLIENT_HEADERS.contains(name.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("header is set by the HTTP client: " + name);
        if (value == null) throw new IllegalArgumentException("header value cannot be null");
        if (value.length() > MAX_VALUE_LENGTH || CONTROL.matcher(value).find())
            throw new IllegalArgumentException("header value is not valid");
    }
}
