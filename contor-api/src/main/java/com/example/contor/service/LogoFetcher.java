package com.example.contor.service;

import com.example.contor.domain.SenderDomain;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fetches the icon of a sender domain from the sender's own web server. Guards against SSRF: HTTPS on port 443 only,
 * every host (including each redirect target) must resolve to public addresses, at most {@value #MAX_REDIRECTS}
 * redirects, {@value #MAX_BYTES} bytes and raster images only (no SVG, which could carry script).
 */
@Slf4j
@Component
public class LogoFetcher {

    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final List<String> PATHS = List.of("/apple-touch-icon.png", "/favicon.ico");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Optional<ImageData> fetch(SenderDomain domain) {
        for (URI uri : candidateUris(domain)) {
            Optional<ImageData> image = get(uri, MAX_REDIRECTS);
            if (image.isPresent()) {
                return image;
            }
        }
        return Optional.empty();
    }

    private Optional<ImageData> get(URI uri, int redirectsLeft) {
        if (!isAllowedUri(uri) || !resolvesToPublicAddresses(uri.getHost())) {
            return Optional.empty();
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "image/png,image/x-icon,image/*;q=0.8")
                .header("User-Agent", "contor/1.0 (sender logo)")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status >= 300 && status < 400 && redirectsLeft > 0) {
                    Optional<String> location = response.headers().firstValue("Location");
                    return location.isPresent() ? get(uri.resolve(location.get()), redirectsLeft - 1) : Optional.empty();
                }
                if (status != 200) {
                    return Optional.empty();
                }
                byte[] bytes = body.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    return Optional.empty();
                }
                return imageTypeOf(bytes).map(type -> new ImageData(bytes, type));
            }
        } catch (IOException | IllegalArgumentException e) {
            log.debug("Logo request {} failed: {}", uri.getHost(), e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** {@code https://<domain>} then its parent; the touch icon (180px) before the small favicon. */
    static List<URI> candidateUris(SenderDomain domain) {
        List<SenderDomain> hosts = new ArrayList<>(List.of(domain));
        domain.parent().ifPresent(hosts::add);
        List<URI> uris = new ArrayList<>();
        for (SenderDomain host : hosts) {
            for (String path : PATHS) {
                uris.add(URI.create("https://" + host.value() + path));
            }
        }
        return uris;
    }

    static boolean isAllowedUri(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private static boolean resolvesToPublicAddresses(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
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

    static boolean isPublicAddress(InetAddress address) {
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

    /** Raster image type from the file signature; empty for anything else (SVG, HTML error pages, …). */
    static Optional<String> imageTypeOf(byte[] bytes) {
        if (startsWith(bytes, 0x89, 'P', 'N', 'G')) return Optional.of("image/png");
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) return Optional.of("image/jpeg");
        if (startsWith(bytes, 'G', 'I', 'F', '8')) return Optional.of("image/gif");
        if (startsWith(bytes, 0, 0, 1, 0)) return Optional.of("image/x-icon");
        if (bytes.length >= 12 && startsWith(bytes, 'R', 'I', 'F', 'F')
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }
}
