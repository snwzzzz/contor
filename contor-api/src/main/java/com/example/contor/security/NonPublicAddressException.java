package com.example.contor.security;

import java.net.UnknownHostException;

/** A host resolved to at least one address that is not public; {@link PublicAddressResolver} refuses to connect. */
public class NonPublicAddressException extends UnknownHostException {

    public NonPublicAddressException(String host) {
        super(host + " does not resolve to public addresses only");
    }
}
