package com.example.contor.mailprovider;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/** Token endpoint answer (RFC 6749 section 5.1), the same for Microsoft Entra ID and Google. */
record OAuthTokenResponse(
        String token_type,
        String scope,
        Long expires_in,
        String access_token,
        String refresh_token,
        String error,
        String error_description) {

    /** Posts a token request; an error answer (4xx) surfaces as the RestClient's exception. */
    static OAuthTokens post(RestClient restClient, String tokenEndpoint, MultiValueMap<String, String> form, String issuer) {
        OAuthTokenResponse response = restClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OAuthTokenResponse.class);
        if (response == null || response.access_token() == null) {
            throw new IllegalStateException("No access token returned from " + issuer + ": "
                    + (response != null ? response.error_description() : "null"));
        }
        Instant expiresAt = Instant.now().plusSeconds(response.expires_in() != null ? response.expires_in() : 3600);
        String refreshToken = response.refresh_token() != null && !response.refresh_token().isBlank() ? response.refresh_token() : null;
        return new OAuthTokens(response.access_token(), refreshToken, expiresAt);
    }
}
