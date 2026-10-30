package com.example.contor.externalapi;

/**
 * Authentication to an external API failed before the call (the token request). {@link #credentialsRejected()} when
 * the server refused the credentials (the user has to fix them), not for outages or malformed answers. The message
 * names the host, never a credential.
 */
public class ApiAuthException extends RuntimeException {

    private final boolean credentialsRejected;

    public ApiAuthException(String message, boolean credentialsRejected) {
        super(message);
        this.credentialsRejected = credentialsRejected;
    }

    public boolean credentialsRejected() {
        return credentialsRejected;
    }
}
