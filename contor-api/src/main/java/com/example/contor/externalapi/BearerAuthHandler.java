package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import org.springframework.stereotype.Component;

/** BEARER: a fixed token (e.g. a personal access token) as {@code Authorization: Bearer <token>}. */
@Component
public class BearerAuthHandler implements ApiAuthHandler {

    @Override
    public ApiAuthKind kind() {
        return ApiAuthKind.BEARER;
    }

    @Override
    public void validate(ApiAuthConfig config, ApiSecret secret) {
        if (!secret.hasValue()) throw new IllegalArgumentException("The token is missing");
    }

    @Override
    public void apply(ApiAuthConfig config, ApiSecret secret, ApiRequest request) {
        request.header("Authorization", "Bearer " + secret.value());
    }
}
