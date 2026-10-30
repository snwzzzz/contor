package com.example.contor.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The secret part of an external API's authentication, stored as encrypted JSON in {@link ExternalApi#getSecret()}:
 * the API key, token, password or client secret ({@link ApiAuthKind} says which) and the secret headers. Never mapped to
 * a DTO, logged or put in a prompt; {@link #toString()} shows no values.
 */
public record ApiSecret(String value, List<ApiHeader> headers) {

    public static final ApiSecret EMPTY = new ApiSecret(null, List.of());

    private static final int MAX_LENGTH = 8000;
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1F\\x7F]");

    public ApiSecret {
        if (value != null && (value.length() > MAX_LENGTH || CONTROL.matcher(value).find()))
            throw new IllegalArgumentException("secret is not valid");
        headers = headers == null ? List.of() : List.copyOf(headers);
    }

    public boolean hasValue() {
        return value != null && !value.isEmpty();
    }

    @Override
    public String toString() {
        return "ApiSecret[value=" + (hasValue() ? "***" : "none") + ", headers=" + headers.stream().map(ApiHeader::name).toList() + "]";
    }
}
