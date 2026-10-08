package com.example.contor.exception;

import lombok.Getter;

/** The Microsoft OAuth callback failed; the browser is still sent back to the web app, to {@code forwardUrl}. */
@Getter
public class MailCallbackException extends RuntimeException {

    private final String forwardUrl;

    public MailCallbackException(String forwardUrl, Throwable cause) {
        super("Mail account callback failed", cause);
        this.forwardUrl = forwardUrl;
    }
}
