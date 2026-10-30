package com.example.contor.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The transport connects to the addresses its resolver checked, not to a second lookup (DNS rebinding). "api.example"
 * does not exist: only the resolver knows it, as the loopback address of a local socket that counts connections.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApacheTransportTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private ServerSocket server;
    private Thread acceptor;
    private final AtomicInteger connections = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        server.setSoTimeout(200);
        acceptor = Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    connections.incrementAndGet();
                } catch (SocketTimeoutException e) {
                    // keep waiting
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
        acceptor.join();
    }

    @Test
    void connects_to_the_address_the_resolver_returned() throws Exception {
        var resolver = new PublicAddressResolver(host -> new InetAddress[] {InetAddress.getLoopbackAddress()}, address -> true);
        try (var transport = new ApacheTransport(resolver)) {
            assertThatThrownBy(() -> transport.exchange("GET", uri(), Map.of(), null, null, TIMEOUT, 100))
                    .isInstanceOf(IOException.class);
        }
        assertThat(connections.get()).isEqualTo(1);
    }

    @Test
    void never_connects_when_the_host_resolves_to_a_non_public_address() throws Exception {
        var resolver = new PublicAddressResolver(host -> new InetAddress[] {InetAddress.getLoopbackAddress()});
        try (var transport = new ApacheTransport(resolver)) {
            assertThatThrownBy(() -> transport.exchange("GET", uri(), Map.of(), null, null, TIMEOUT, 100))
                    .isInstanceOf(NonPublicAddressException.class);
        }
        Thread.sleep(300);
        assertThat(connections.get()).isZero();
    }

    private URI uri() {
        return URI.create("https://api.example:" + server.getLocalPort() + "/orders");
    }
}
