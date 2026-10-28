package com.example.contor.security;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The way contor calls hosts named by users or found on the web (sender logos, external APIs; ADR 0012). Guards
 * against SSRF: HTTPS on port 443 only, no user info, GET and POST only, at most {@value #MAX_REDIRECTS} redirects (GET
 * only, each target checked again), a timeout per request and a cap on the bytes read. Every host must resolve to public
 * addresses only, and the connection is made to exactly the addresses checked ({@link PublicAddressResolver}), so DNS
 * rebinding cannot reach the machine or the local network. Messages name the host only: paths and query strings may
 * carry API keys.
 */
@Component
public class OutboundRequestGuard implements AutoCloseable {

    public static final int MAX_REDIRECTS = 3;
    private static final Set<String> METHODS = Set.of("GET", "POST");

    private final Transport transport;

    public OutboundRequestGuard() {
        this(new ApacheTransport(new PublicAddressResolver()));
    }

    OutboundRequestGuard(Transport transport) {
        this.transport = transport;
    }

    /**
     * @param body        {@code null} for none
     * @param contentType of the body, {@code null} without one
     */
    public record Request(String method, URI uri, Map<String, String> headers, byte[] body, String contentType,
                          Duration timeout, int maxBytes) {

        public static Request get(URI uri, Map<String, String> headers, Duration timeout, int maxBytes) {
            return new Request("GET", uri, headers, null, null, timeout, maxBytes);
        }
    }

    /**
     * @param headers   first value per header, lower-case names
     * @param truncated the body was longer than the limit and is cut there
     */
    public record Response(int status, Map<String, String> headers, byte[] body, boolean truncated) {

        public String contentType() {
            return headers.get("content-type");
        }

        public Optional<String> header(String name) {
            return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
        }
    }

    /** GETs {@code uri}; see {@link #send}. */
    public Response get(URI uri, Map<String, String> headers, Duration timeout, int maxBytes) {
        return send(Request.get(uri, headers, timeout, maxBytes));
    }

    /**
     * Sends {@code request}, following GET redirects to allowed hosts. Any status is returned as it is (a POST redirect
     * too).
     *
     * @throws OutboundRequestException when the URI, a redirect target or the method is not allowed, the host does not
     *                                  resolve to public addresses only, or the request fails
     */
    public Response send(Request request) {
        if (!METHODS.contains(request.method())) {
            throw new OutboundRequestException("Only GET and POST are allowed (" + request.uri().getHost() + ")");
        }
        URI current = request.uri();
        for (int redirects = 0; ; redirects++) {
            check(current);
            Transport.Exchange exchange = exchange(request, current);
            String location = exchange.headers().get("location");
            boolean redirect = exchange.status() >= 300 && exchange.status() < 400 && location != null;
            if (!redirect || !"GET".equals(request.method())) {
                return new Response(exchange.status(), exchange.headers(), exchange.body(), exchange.truncated());
            }
            if (redirects == MAX_REDIRECTS) {
                throw new OutboundRequestException("More than " + MAX_REDIRECTS + " redirects from " + request.uri().getHost());
            }
            try {
                current = current.resolve(location);
            } catch (IllegalArgumentException e) {
                throw new OutboundRequestException(current.getHost() + " redirected to an invalid location", e);
            }
        }
    }

    private Transport.Exchange exchange(Request request, URI uri) {
        try {
            return transport.exchange(request.method(), uri, request.headers(), request.body(), request.contentType(),
                    request.timeout(), request.maxBytes());
        } catch (NonPublicAddressException e) {
            throw new OutboundRequestException(uri.getHost() + " does not resolve to public addresses only", e);
        } catch (IOException e) {
            throw new OutboundRequestException("Request to " + uri.getHost() + " failed", e);
        } catch (IllegalArgumentException e) {
            throw new OutboundRequestException("Request to " + uri.getHost() + " is invalid", e);
        }
    }

    private static void check(URI uri) {
        if (!isAllowedUri(uri)) {
            throw new OutboundRequestException("Only HTTPS on port 443 is allowed (" + uri.getHost() + ")");
        }
    }

    public static boolean isAllowedUri(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
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

    @Override
    public void close() {
        transport.close();
    }
}
