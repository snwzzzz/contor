package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Login that the dev profile keeps ready (created or reset on every start). Values come from .env, never from code. */
@ConfigurationProperties("app.dev-account")
public record DevAccountProperties(String username, String email, String password) {

    public boolean isConfigured() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
