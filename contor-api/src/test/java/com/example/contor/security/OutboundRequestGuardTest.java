package com.example.contor.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboundRequestGuardTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final FakeTransport transport = new FakeTransport();
    private final OutboundRequestGuard guard = new OutboundRequestGuard(transport);

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
        assertThat(transport.requests).isEmpty();
    }

    @Test
    void refuses_methods_other_than_get_and_post() {
        assertThatThrownBy(() -> guard.send(new OutboundRequestGuard.Request("DELETE", URI.create("https://api.example/orders/1"),
                Map.of(), null, null, TIMEOUT, 100))).isInstanceOf(OutboundRequestException.class);
        assertThat(transport.requests).isEmpty();
    }

    @Test
    void reports_hosts_that_do_not_resolve_to_public_addresses() {
        transport.fail(new NonPublicAddressException("intranet.example"));

        assertThatThrownBy(() -> guard.get(URI.create("https://intranet.example/x"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class)
                .hasMessageContaining("intranet.example does not resolve to public addresses only");
    }

    @Test
    void returns_status_headers_and_body() {
        transport.answer(200, Map.of("content-type", "application/json", "retry-after", "120"), "{\"ok\":true}");

        OutboundRequestGuard.Response response = guard.get(URI.create("https://api.example/orders"), Map.of(), TIMEOUT, 100);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.header("Retry-After")).contains("120");
        assertThat(response.header("ETag")).isEmpty();
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void sends_method_headers_body_timeout_and_limit() {
        transport.answer(200, Map.of(), "");
        byte[] body = "{\"query\":\"x\"}".getBytes(StandardCharsets.UTF_8);

        guard.send(new OutboundRequestGuard.Request("POST", URI.create("https://api.example/graphql"),
                Map.of("Accept", "application/json"), body, "application/json", TIMEOUT, 100));

        var sent = transport.requests.getFirst();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.headers()).containsEntry("Accept", "application/json");
        assertThat(sent.body()).isEqualTo(body);
        assertThat(sent.contentType()).isEqualTo("application/json");
        assertThat(sent.timeout()).isEqualTo(TIMEOUT);
        assertThat(sent.maxBytes()).isEqualTo(100);
    }

    @Test
    void passes_on_the_truncated_body() {
        transport.answers.add(new Transport.Exchange(200, Map.of(), "0123".getBytes(StandardCharsets.UTF_8), true));

        OutboundRequestGuard.Response response = guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 4);

        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("0123");
        assertThat(response.truncated()).isTrue();
    }

    @Test
    void follows_get_redirects_relative_and_to_other_hosts() {
        transport.redirect("/v2/orders");
        transport.redirect("https://cdn.example/orders.json");
        transport.answer(200, Map.of(), "[]");

        OutboundRequestGuard.Response response = guard.get(URI.create("https://api.example/orders"), Map.of("Accept", "*/*"), TIMEOUT, 100);

        assertThat(response.status()).isEqualTo(200);
        assertThat(transport.requests).extracting(FakeTransport.Sent::uri).containsExactly(
                URI.create("https://api.example/orders"),
                URI.create("https://api.example/v2/orders"),
                URI.create("https://cdn.example/orders.json"));
        assertThat(transport.requests).allSatisfy(sent -> assertThat(sent.headers()).containsEntry("Accept", "*/*"));
    }

    @Test
    void checks_every_redirect_target() {
        transport.redirect("http://api.example/plain");

        assertThatThrownBy(() -> guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class);
        assertThat(transport.requests).hasSize(1);
    }

    @Test
    void stops_after_three_redirects() {
        for (int i = 0; i <= OutboundRequestGuard.MAX_REDIRECTS; i++) {
            transport.redirect("/next");
        }

        assertThatThrownBy(() -> guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class);
        assertThat(transport.requests).hasSize(OutboundRequestGuard.MAX_REDIRECTS + 1);
    }

    @Test
    void returns_a_post_redirect_as_it_is() {
        transport.redirect("https://api.example/elsewhere");

        var response = guard.send(new OutboundRequestGuard.Request("POST", URI.create("https://api.example/search"),
                Map.of(), new byte[0], "application/json", TIMEOUT, 100));

        assertThat(response.status()).isEqualTo(302);
        assertThat(transport.requests).hasSize(1);
    }

    @Test
    void returns_a_redirect_without_location_as_it_is() {
        transport.answer(304, Map.of(), "");

        assertThat(guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 100).status()).isEqualTo(304);
    }

    @Test
    void wraps_network_failures() {
        transport.fail(new IOException("connection reset"));

        assertThatThrownBy(() -> guard.get(URI.create("https://api.example/"), Map.of(), TIMEOUT, 100))
                .isInstanceOf(OutboundRequestException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void never_puts_the_path_or_query_into_its_messages() {
        assertThatThrownBy(() -> guard.get(URI.create("http://intranet.example/private?api_key=secret-123"), Map.of(), TIMEOUT, 100))
                .message().contains("intranet.example").doesNotContain("secret-123").doesNotContain("/private");

        transport.fail(new IOException("failed https://api.example/x?api_key=secret-123"));
        assertThatThrownBy(() -> guard.get(URI.create("https://api.example/x?api_key=secret-123"), Map.of(), TIMEOUT, 100))
                .message().doesNotContain("secret-123");
    }

    private static InetAddress ip(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Answers from a queue (or fails) and records what was sent. */
    private static final class FakeTransport implements Transport {

        record Sent(String method, URI uri, Map<String, String> headers, byte[] body, String contentType, Duration timeout, int maxBytes) {
        }

        final List<Sent> requests = new ArrayList<>();
        final Deque<Transport.Exchange> answers = new ArrayDeque<>();
        private IOException failure;

        void answer(int status, Map<String, String> headers, String body) {
            answers.add(new Transport.Exchange(status, headers, body.getBytes(StandardCharsets.UTF_8), false));
        }

        void redirect(String location) {
            answer(302, Map.of("location", location), "");
        }

        void fail(IOException failure) {
            this.failure = failure;
        }

        @Override
        public Exchange exchange(String method, URI uri, Map<String, String> headers, byte[] body, String contentType,
                                 Duration timeout, int maxBytes) throws IOException {
            requests.add(new Sent(method, uri, headers, body, contentType, timeout, maxBytes));
            if (failure != null) {
                throw failure;
            }
            return answers.removeFirst();
        }

        @Override
        public void close() {
        }
    }
}
