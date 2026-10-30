package com.example.contor.service;

import com.example.contor.domain.SenderDomain;
import com.example.contor.security.OutboundRequestException;
import com.example.contor.security.OutboundRequestGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches the icon of a sender domain from the sender's own web server, through {@link OutboundRequestGuard} (HTTPS,
 * public hosts only, redirects checked). At most {@value #MAX_BYTES} bytes and raster images only (no SVG, which could
 * carry script).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LogoFetcher {

    private static final int MAX_BYTES = 256 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final List<String> PATHS = List.of("/apple-touch-icon.png", "/favicon.ico");
    private static final Map<String, String> HEADERS = Map.of(
            "Accept", "image/png,image/x-icon,image/*;q=0.8",
            "User-Agent", "contor/1.0 (sender logo)");

    private final OutboundRequestGuard guard;

    public Optional<ImageData> fetch(SenderDomain domain) {
        for (URI uri : candidateUris(domain)) {
            Optional<ImageData> image = get(uri);
            if (image.isPresent()) {
                return image;
            }
        }
        return Optional.empty();
    }

    private Optional<ImageData> get(URI uri) {
        try {
            OutboundRequestGuard.Response response = guard.get(uri, HEADERS, TIMEOUT, MAX_BYTES);
            if (response.status() != 200 || response.truncated()) {
                return Optional.empty();
            }
            return imageTypeOf(response.body()).map(type -> new ImageData(response.body(), type));
        } catch (OutboundRequestException e) {
            log.debug("Logo request {} failed: {}", uri.getHost(), e.getMessage());
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
