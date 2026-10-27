package com.example.contor.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboundRequestGuardTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final Map<String, InetAddress[]> DNS = Map.of(
            "api.example", new InetAddress[] {ip(8, 8, 8, 8)},
            "cdn.example", new InetAddress[] {ip(1, 1, 1, 1)},
            "intranet.example", new InetAddress[] {ip(10, 0, 0, 5)},
            "mixed.example", new InetAddress[] {ip(8, 8, 4, 4), ip(192, 168, 1, 1)},
            "evil.example", new InetAddress[] {ip(127, 0, 0, 1)});

    private final HttpClient client = mock(HttpClient.class);
    private final OutboundRequestGuard guard = new OutboundRequestGuard(client, OutboundRequestGuardTest::resolve);

    @Test
    void rejects_loopback_private_link_local_and_shared_addresses() throws UnknownHostException {
        assertThat(OutboundRequestGuard.isPublicAddress(ip(127, 0, 0, 1))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(ip(10, 1, 2, 3))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(ip(172, 16, 0, 1))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(ip(192, 168, 1, 1))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(ip(169, 254, 169, 254))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(ip(100, 64, 0, 1))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(ip(0, 0, 0, 0))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(InetAddress.getByName("::1"))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(InetAddress.getByName("fd00::1"))).isFalse();
        assertThat(OutboundRequestGuard.isPublicAddress(InetAddress.getByName("::ffff:192.168.1.1"))).isFalse();
    }

    @Test
    void accepts_public_addresses() throws UnknownHostException {
        assertThat(OutboundRequestGuard.isPublicAddress(ip(8, 8, 8, 8))).isTrue();
        assertThat(OutboundRequestGuard.isPublicAddress(InetAddress.getByName("2a00:1450:4001:80b::200e"))).isTrue();
    }

    @Test
    void allows_only_https_on_the_default_port_without_user_info() {
        assertThat(OutboundRequestGuard.isAllowedUri(URI.create("https://siemens.com/favicon.ico"))).isTrue();
        assertThat(OutboundRequestGuard.isAllowedUri(URI.create("https://siemens.com:443/favicon.ico"))).isTrue();
        assertThat(OutboundRequestGuard.isAllowedUri(URI.create("http://siemens.com/favicon.ico"))).isFalse();
        assertThat(OutboundRequestGuard.isAllowedUri(URI.create("https://siemens.com:8443/favicon.ico"))).isFalse();
        assertThat(OutboundRequestGuard.isAllowedUri(URI.create("https://user@siemens.com/favicon.ico"))).isFalse();
        assertThat(OutboundRequestGuard.isAllowedUri(URI.create("file:///etc/passwd"))).isFalse();
    }

    @Test
    void refuses_plain_http_without_sending_anything() {
        assertThatThrownBy(() -> guard.get(URI.create("http://api.example/orders"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class);
        verifyNoInteractions(client);
    }

    @Test
    void refuses_hosts_that_resolve_to_a_private_address() {
        assertThatThrownBy(() -> guard.get(URI.create("https://intranet.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class);
        assertThatThrownBy(() -> guard.get(URI.create("https://mixed.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class);
        verifyNoInteractions(client);
    }

    @Test
    void refuses_hosts_that_do_not_resolve() {
        assertThatThrownBy(() -> guard.get(URI.create("https://unknown.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class);
        verifyNoInteractions(client);
    }

    @Test
    void returns_status_content_type_and_body() throws Exception {
        doReturn(response(200, Map.of("Content-Type", List.of("application/json")), "{\"ok\":true}")).when(client).send(any(), any());

        OutboundRequestGuard.Response response = guard.get(URI.create("https://api.example/orders"), Map.of(), TIMEOUT, 100);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void sends_the_given_headers_with_the_timeout() throws Exception {
        doReturn(response(200, Map.of(), "")).when(client).send(any(), any());

        guard.get(URI.create("https://api.example/orders"), Map.of("Accept", "application/json"), TIMEOUT, 100);

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("GET");
        assertThat(request.getValue().headers().firstValue("Accept")).contains("application/json");
        assertThat(request.getValue().timeout()).contains(TIMEOUT);
    }

    @Test
    void cuts_the_body_at_the_limit_and_marks_it_truncated() throws Exception {
        doReturn(response(200, Map.of(), "0123456789")).when(client).send(any(), any());

        OutboundRequestGuard.Response response = guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 4);

        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("0123");
        assertThat(response.truncated()).isTrue();
    }

    @Test
    void follows_redirects_relative_and_to_other_public_hosts() throws Exception {
        doReturn(redirect("/v2/orders"), redirect("https://cdn.example/orders.json"), response(200, Map.of(), "[]"))
                .when(client).send(any(), any());

        OutboundRequestGuard.Response response = guard.get(URI.create("https://api.example/orders"), Map.of(), TIMEOUT, 100);

        assertThat(response.status()).isEqualTo(200);
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(3)).send(requests.capture(), any());
        assertThat(requests.getAllValues()).extracting(HttpRequest::uri).containsExactly(
                URI.create("https://api.example/orders"),
                URI.create("https://api.example/v2/orders"),
                URI.create("https://cdn.example/orders.json"));
    }

    @Test
    void checks_every_redirect_target() throws Exception {
        doReturn(redirect("https://evil.example/admin")).when(client).send(any(), any());

        assertThatThrownBy(() -> guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class);
        verify(client, times(1)).send(any(), any());
    }

    @Test
    void stops_after_three_redirects() throws Exception {
        doReturn(redirect("/next")).when(client).send(any(), any());

        assertThatThrownBy(() -> guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class);
        verify(client, times(OutboundRequestGuard.MAX_REDIRECTS + 1)).send(any(), any());
    }

    @Test
    void returns_a_redirect_without_location_as_it_is() throws Exception {
        doReturn(response(304, Map.of(), "")).when(client).send(any(), any());

        assertThat(guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 100).status()).isEqualTo(304);
    }

    @Test
    void wraps_network_failures() throws Exception {
        doThrow(new IOException("connection reset")).when(client).send(any(), any());

        assertThatThrownBy(() -> guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void never_puts_the_path_or_query_into_its_messages() throws Exception {
        assertThatThrownBy(() -> guard.get(URI.create("https://intranet.example/private?api_key=secret-123"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class)
                .message().contains("intranet.example").doesNotContain("secret-123").doesNotContain("/private");

        doThrow(new IOException("failed https://api.example/x?api_key=secret-123")).when(client).send(any(), any());
        assertThatThrownBy(() -> guard.get(URI.create("https://api.example/x?api_key=secret-123"), Map.of(), TIMEOUT, 100))
                .message().doesNotContain("secret-123");
    }

    private static HttpResponse<InputStream> redirect(String location) {
        return response(302, Map.of("Location", List.of(location)), "");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, Map<String, List<String>> headers, String body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

    private static InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = DNS.get(host);
        if (addresses == null) {
            throw new UnknownHostException(host);
        }
        return addresses;
    }

    private static InetAddress ip(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
