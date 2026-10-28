package com.example.contor.domain;

/** How contor authenticates to an external API; {@link ApiAuthConfig} holds the non-secret settings, the secret is encrypted. */
public enum ApiAuthKind {
    NONE, API_KEY_HEADER, API_KEY_QUERY, BEARER, BASIC, OAUTH2_CLIENT_CREDENTIALS
}
