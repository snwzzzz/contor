package com.example.contor.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PublicAddressResolverTest {

    private static final Map<String, InetAddress[]> DNS = Map.of(
            "api.example", new InetAddress[] {ip(8, 8, 8, 8), ip(8, 8, 4, 4)},
            "intranet.example", new InetAddress[] {ip(10, 0, 0, 5)},
            "mixed.example", new InetAddress[] {ip(8, 8, 4, 4), ip(192, 168, 1, 1)});

    private final PublicAddressResolver resolver = new PublicAddressResolver(PublicAddressResolverTest::lookup);

    @Test
    void returns_every_address_of_a_public_host() throws UnknownHostException {
        assertThat(resolver.resolve("api.example")).containsExactly(ip(8, 8, 8, 8), ip(8, 8, 4, 4));
    }

    @Test
    void refuses_a_host_with_any_non_public_address() {
        assertThatThrownBy(() -> resolver.resolve("intranet.example")).isInstanceOf(NonPublicAddressException.class)
                .hasMessageContaining("intranet.example");
        assertThatThrownBy(() -> resolver.resolve("mixed.example")).isInstanceOf(NonPublicAddressException.class);
    }

    @Test
    void refuses_ip_literals_of_the_machine_and_the_local_network() {
        var real = new PublicAddressResolver();

        assertThatThrownBy(() -> real.resolve("127.0.0.1")).isInstanceOf(NonPublicAddressException.class);
        assertThatThrownBy(() -> real.resolve("169.254.169.254")).isInstanceOf(NonPublicAddressException.class);
        assertThatThrownBy(() -> real.resolve("::1")).isInstanceOf(NonPublicAddressException.class);
    }

    @Test
    void unknown_hosts_stay_unknown() {
        assertThatThrownBy(() -> resolver.resolve("unknown.example")).isInstanceOf(UnknownHostException.class)
                .isNotInstanceOf(NonPublicAddressException.class);
    }

    @Test
    void never_does_a_reverse_lookup() throws UnknownHostException {
        assertThat(resolver.resolveCanonicalHostname("api.example")).isEqualTo("api.example");
    }

    private static InetAddress[] lookup(String host) throws UnknownHostException {
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
