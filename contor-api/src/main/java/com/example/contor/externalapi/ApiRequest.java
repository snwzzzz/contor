package com.example.contor.externalapi;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Headers and query parameters of one external API call while they are collected: plain headers, secret headers, then
 * what the {@link ApiAuthHandler} adds. A later header replaces an earlier one of the same name (any case); a later
 * query parameter replaces an earlier one of the same name.
 */
public class ApiRequest {

    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> query = new LinkedHashMap<>();

    public void header(String name, String value) {
        headers.remove(name);
        headers.put(name, value);
    }

    public void queryParam(String name, String value) {
        query.put(name, value);
    }

    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }

    public Map<String, String> query() {
        return Collections.unmodifiableMap(query);
    }

    /** {@code base} (without a query) with the encoded query parameters appended. */
    public URI uri(URI base) {
        if (query.isEmpty()) {
            return base;
        }
        return URI.create(base + "?" + query.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&")));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
