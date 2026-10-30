package com.example.contor.security;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@link Transport} on Apache HttpClient 5. Every connection's addresses come from the given {@link DnsResolver}
 * (normally {@link PublicAddressResolver}), never from a proxy or a second lookup. No redirects, retries, cookies or
 * authentication caching; the default TLS trust store and hostname verification. A body over the limit is not read to
 * the end: the connection is dropped instead.
 */
final class ApacheTransport implements Transport {

    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(5);

    private final CloseableHttpClient client;

    ApacheTransport(DnsResolver dnsResolver) {
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).build())
                        .setMaxConnTotal(20)
                        .setMaxConnPerRoute(4)
                        .build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .build();
    }

    @Override
    public Exchange exchange(String method, URI uri, Map<String, String> headers, byte[] body, String contentType,
                             Duration timeout, int maxBytes) throws IOException {
        HttpUriRequestBase request = new HttpUriRequestBase(method, uri);
        headers.forEach(request::addHeader);
        if (body != null) {
            request.setEntity(new ByteArrayEntity(body, contentType == null ? null : ContentType.parse(contentType)));
        }
        Timeout limit = Timeout.of(timeout);
        request.setConfig(RequestConfig.custom().setResponseTimeout(limit).setConnectionRequestTimeout(limit).build());
        ClassicHttpResponse response;
        try {
            response = client.executeOpen(RoutingSupport.determineHost(request), request, null);
        } catch (HttpException e) {
            throw new IOException("Invalid request", e);
        }
        // Closing a response (or its stream) reads the rest of the body; only a body read to its end is closed,
        // anything else is aborted, which drops the connection.
        boolean readToEnd = false;
        try {
            Map<String, String> responseHeaders = new HashMap<>();
            for (Header header : response.getHeaders()) {
                responseHeaders.putIfAbsent(header.getName().toLowerCase(Locale.ROOT), header.getValue());
            }
            byte[] bytes = new byte[0];
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream in = entity.getContent();
                bytes = in.readNBytes(maxBytes + 1);
            }
            boolean truncated = bytes.length > maxBytes;
            readToEnd = !truncated;
            return new Exchange(response.getCode(), responseHeaders, truncated ? Arrays.copyOf(bytes, maxBytes) : bytes, truncated);
        } finally {
            if (readToEnd) {
                response.close();
            } else {
                request.abort();
            }
        }
    }

    @Override
    public void close() {
        client.close(CloseMode.IMMEDIATE);
    }
}
