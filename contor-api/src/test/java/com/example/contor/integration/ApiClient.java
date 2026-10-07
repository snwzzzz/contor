package com.example.contor.integration;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal browser stand-in for integration tests: keeps cookies per client and echoes the
 * XSRF-TOKEN cookie as X-XSRF-TOKEN header on mutating requests (like contor-web's fetcher).
 */
class ApiClient {

    record Response(int status, String body, List<String> setCookies, HttpHeaders headers) {
    }

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;
    private final Map<String, String> extraHeaders = new LinkedHashMap<>();

    ApiClient(int port) {
        this.baseUrl = "http://127.0.0.1:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    /** Header sent with every following request (e.g. X-Forwarded-For as a proxy would add it). */
    ApiClient withHeader(String name, String value) {
        extraHeaders.put(name, value);
        return this;
    }

    Response get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET(), false);
    }

    Response post(String path, String json) {
        return send(HttpRequest.newBuilder(uri(path)).POST(body(json)).header("Content-Type", "application/json"), true);
    }

    Response put(String path, String json) {
        return send(HttpRequest.newBuilder(uri(path)).PUT(body(json)).header("Content-Type", "application/json"), true);
    }

    Response postWithoutCsrf(String path, String json) {
        return send(HttpRequest.newBuilder(uri(path)).POST(body(json)).header("Content-Type", "application/json"), false);
    }

    void csrf() {
        get("/api/auth/csrf");
    }

    Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    void setCookie(String name, String value) {
        cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .toList()
                .forEach(c -> cookies.getCookieStore().remove(URI.create(baseUrl), c));
        var cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        cookie.setVersion(0);
        cookies.getCookieStore().add(URI.create(baseUrl), cookie);
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private static HttpRequest.BodyPublisher body(String json) {
        return json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json);
    }

    private Response send(HttpRequest.Builder builder, boolean withCsrf) {
        extraHeaders.forEach(builder::header);
        if (withCsrf) {
            if (cookie("XSRF-TOKEN").isEmpty()) csrf();
            cookie("XSRF-TOKEN").ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
        }
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response.headers().allValues("set-cookie"), response.headers());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
