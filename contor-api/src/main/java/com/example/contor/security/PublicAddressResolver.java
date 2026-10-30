package com.example.contor.security;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Predicate;

/**
 * The only DNS lookup of {@link ApacheTransport}: it returns a host's addresses only when every one is public, and the
 * connection is made to exactly those. So checking and connecting are one lookup, and DNS rebinding (a public answer for
 * the check, a private one for the connection) has no second lookup to exploit. No reverse lookups.
 */
public class PublicAddressResolver implements DnsResolver {

    /** Host name lookup (the test seam for DNS). */
    @FunctionalInterface
    interface Lookup {
        InetAddress[] lookup(String host) throws UnknownHostException;
    }

    private final Lookup lookup;
    private final Predicate<InetAddress> allowed;

    public PublicAddressResolver() {
        this(InetAddress::getAllByName);
    }

    PublicAddressResolver(Lookup lookup) {
        this(lookup, OutboundRequestGuard::isPublicAddress);
    }

    PublicAddressResolver(Lookup lookup, Predicate<InetAddress> allowed) {
        this.lookup = lookup;
        this.allowed = allowed;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = lookup.lookup(host);
        if (addresses.length == 0) {
            throw new UnknownHostException(host);
        }
        for (InetAddress address : addresses) {
            if (!allowed.test(address)) {
                throw new NonPublicAddressException(host);
            }
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }
}
