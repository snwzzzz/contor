package com.example.contor.security;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * The way contor calls hosts named by users or found on the web (sender logos; later external APIs, ADR 0012). Guards
 * against SSRF: HTTPS on port 443 only, no user info, every host – each redirect target included – must resolve to
 * public addresses only, at most {@value #MAX_REDIRECTS} redirects, a timeout per request and a cap on the bytes read.
 * Messages name the host only: paths and query strings may carry API keys.
 * <p>
 * The JDK client resolves the host again when it connects, so DNS rebinding (a public address for the check, a private
 * one for the connection) is not stopped here. ADR 0012 has the plan for that before credentials are sent.
 */
@Component
public class OutboundRequestGuard {

    public static final int MAX_REDIRECTS = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client;
    private final Resolver resolver;

    public OutboundRequestGuard() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build(),
                InetAddress::getAllByName);
    }

    OutboundRequestGuard(HttpClient client, Resolver resolver) {
        this.client = client;
        this.resolver = resolver;
    }

    /** Host name lookup (the test seam for DNS). */
    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /**
     * @param contentType the {@code Content-Type} header, {@code null} when missing
     * @param truncated   the body was longer than the limit and is cut there
     */
    public record Response(int status, String contentType, byte[] body, boolean truncated) {
    }

    /**
     * GETs {@code uri}, following redirects to allowed hosts. Any status is returned as it is.
     *
     * @throws OutboundRequestException when the URI or a redirect target is not allowed, or the request fails
     */
    public Response get(URI uri, Map<String, String> headers, Duration timeout, int maxBytes) {
        URI current = uri;
        for (int redirects = 0; ; redirects++) {
            check(current);
            HttpResponse<InputStream> response = send(current, headers, timeout);
            String host = current.getHost();
            try (InputStream body = response.body()) {
                Optional<String> location = response.headers().firstValue("Location");
                if (response.statusCode() >= 300 && response.statusCode() < 400 && location.isPresent()) {
                    if (redirects == MAX_REDIRECTS) {
                        throw new OutboundRequestException("More than " + MAX_REDIRECTS + " redirects from " + uri.getHost());
                    }
                    current = current.resolve(location.get());
                    continue;
                }
                byte[] bytes = body.readNBytes(maxBytes + 1);
                boolean truncated = bytes.length > maxBytes;
                return new Response(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                        truncated ? Arrays.copyOf(bytes, maxBytes) : bytes, truncated);
            } catch (IOException e) {
                throw new OutboundRequestException("Reading the answer of " + host + " failed", e);
            } catch (IllegalArgumentException e) {
                throw new OutboundRequestException(host + " redirected to an invalid location", e);
            }
        }
    }

    private HttpResponse<InputStream> send(URI uri, Map<String, String> headers, Duration timeout) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout).GET();
            headers.forEach(request::header);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new OutboundRequestException("Request to " + uri.getHost() + " failed", e);
        } catch (IllegalArgumentException e) {
            throw new OutboundRequestException("Request to " + uri.getHost() + " is invalid", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboundRequestException("Request to " + uri.getHost() + " was interrupted", e);
        }
    }

    private void check(URI uri) {
        if (!isAllowedUri(uri)) {
            throw new OutboundRequestException("Only HTTPS on port 443 is allowed (" + uri.getHost() + ")");
        }
        if (!resolvesToPublicAddresses(uri.getHost())) {
            throw new OutboundRequestException(uri.getHost() + " does not resolve to public addresses only");
        }
    }

    public static boolean isAllowedUri(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private boolean resolvesToPublicAddresses(String host) {
        try {
            InetAddress[] addresses = resolver.resolve(host);
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
            return addresses.length > 0;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            boolean sharedAddressSpace = first == 100 && second >= 64 && second <= 127;
            return first != 0 && !sharedAddressSpace;
        }
        if (address instanceof Inet6Address) {
            boolean uniqueLocal = (bytes[0] & 0xFE) == 0xFC;
            return !uniqueLocal;
        }
        return false;
    }
}
