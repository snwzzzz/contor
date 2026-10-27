package com.example.contor.security;

/** A request {@link OutboundRequestGuard} refused, or one that failed. The message names the host, never the full URL. */
public class OutboundRequestException extends RuntimeException {

    public OutboundRequestException(String message) {
        super(message);
    }

    public OutboundRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
