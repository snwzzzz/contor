package com.example.contor.security;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** One HTTP exchange without redirects, for {@link OutboundRequestGuard} (the test seam for the network). */
interface Transport extends AutoCloseable {

    /**
     * @param body        {@code null} for none
     * @param contentType of the body, {@code null} without one
     * @param maxBytes    the body is read up to this many bytes; the rest is dropped with the connection
     */
    Exchange exchange(String method, URI uri, Map<String, String> headers, byte[] body, String contentType,
                      Duration timeout, int maxBytes) throws IOException;

    @Override
    void close();

    /** @param headers first value per header, lower-case names */
    record Exchange(int status, Map<String, String> headers, byte[] body, boolean truncated) {
    }
}
