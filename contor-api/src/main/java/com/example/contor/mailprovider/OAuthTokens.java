package com.example.contor.mailprovider;

import java.time.Instant;

/** Tokens from a code exchange or refresh; {@code refreshToken} is null when the provider sent none. */
public record OAuthTokens(String accessToken, String refreshToken, Instant expiresAt) {
}
