package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;

/**
 * Authenticates the calls of one {@link ApiAuthKind}. To support another kind, add an {@link ApiAuthKind} constant (and
 * the migration's {@code ck_external_api_auth_kind}) and a bean implementing this interface. {@link ApiAuthRegistry}
 * picks it up automatically; nothing else changes.
 */
public interface ApiAuthHandler {

    ApiAuthKind kind();

    /**
     * Checks that the settings and the secret this kind needs are there.
     *
     * @throws IllegalArgumentException naming the missing setting, never a secret value
     */
    void validate(ApiAuthConfig config, ApiSecret secret);

    /** Adds the credentials to {@code request}; call {@link #validate} first. */
    void apply(ApiAuthConfig config, ApiSecret secret, ApiRequest request);
}
