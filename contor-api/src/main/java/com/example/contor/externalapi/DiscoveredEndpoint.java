package com.example.contor.externalapi;

import com.example.contor.domain.ApiPath;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.EndpointSource;
import com.example.contor.domain.JsonPathExpr;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * An endpoint the documentation suggests (parsed from an OpenAPI document or read by the model), for the user to
 * confirm. Always a GET below the app's base URL: a suggestion never picks the method, host or anything above the base
 * path (ADR 0012).
 *
 * @param query       fixed query parameters, in order
 * @param itemsPath   {@code null} when unknown
 * @param itemKeyPath {@code null} when unknown
 * @param description {@code null} when the docs give none
 */
public record DiscoveredEndpoint(String name, ApiPath path, Map<String, String> query, JsonPathExpr itemsPath,
                                 JsonPathExpr itemKeyPath, String description, EndpointSource source) {

    /** Suggestions per run at most. */
    public static final int MAX_ENDPOINTS = 30;
    public static final int MAX_NAME = 80;
    public static final int MAX_DESCRIPTION = 1000;

    public DiscoveredEndpoint {
        query = Collections.unmodifiableMap(new LinkedHashMap<>(query));
    }

    /**
     * {@code raw} as a path below {@code base}: a relative path (a missing leading slash is added) or an absolute URL
     * under the base URL. Empty for other hosts or base paths, {@code ..}, templates ({@code {id}}, {@code :id}), a
     * query, or anything that is no valid {@link ApiPath}.
     */
    public static Optional<ApiPath> relativePath(String raw, BaseUrl base) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String path = raw.strip();
        if (path.regionMatches(true, 0, "https://", 0, 8) || path.regionMatches(true, 0, "http://", 0, 7)) {
            URI uri;
            try {
                uri = URI.create(path);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            URI baseUri = URI.create(base.value());
            String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath();
            String basePath = baseUri.getRawPath() == null ? "" : baseUri.getRawPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || !uri.getHost().equalsIgnoreCase(baseUri.getHost())
                    || uri.getPort() != baseUri.getPort() || uri.getRawQuery() != null || !rawPath.startsWith(basePath + "/")) {
                return Optional.empty();
            }
            path = rawPath.substring(basePath.length());
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.contains("?") || path.contains("{") || path.contains("}") || path.contains("/:")) return Optional.empty();
        try {
            return Optional.of(new ApiPath(path));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Without repeats (same path and query), names unique ignoring case ({@code "Orders (2)"}), at most {@link #MAX_ENDPOINTS}. */
    public static List<DiscoveredEndpoint> distinct(List<DiscoveredEndpoint> endpoints) {
        Set<String> seen = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<DiscoveredEndpoint> result = new ArrayList<>();
        for (DiscoveredEndpoint endpoint : endpoints) {
            if (result.size() == MAX_ENDPOINTS) break;
            if (!seen.add(endpoint.path().value() + "?" + new TreeMap<>(endpoint.query()))) continue;
            String name = endpoint.name();
            for (int number = 2; names.contains(name.toLowerCase(Locale.ROOT)); number++) {
                name = endpoint.name() + " (" + number + ")";
            }
            names.add(name.toLowerCase(Locale.ROOT));
            result.add(new DiscoveredEndpoint(name, endpoint.path(), endpoint.query(), endpoint.itemsPath(), endpoint.itemKeyPath(),
                    endpoint.description(), endpoint.source()));
        }
        return List.copyOf(result);
    }

    /** {@code text} cut to {@code max} characters, {@code null} when blank. */
    public static String clip(String text, int max) {
        if (text == null || text.isBlank()) return null;
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max).strip();
    }
}
