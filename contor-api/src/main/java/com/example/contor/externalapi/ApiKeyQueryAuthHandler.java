package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import org.springframework.stereotype.Component;

/**
 * API_KEY_QUERY: the key as the query parameter {@link ApiAuthConfig#queryParam()}. The URL then carries the key, so a
 * URL is never logged or stored (the guard's messages name the host only).
 */
@Component
public class ApiKeyQueryAuthHandler implements ApiAuthHandler {

    @Override
    public ApiAuthKind kind() {
        return ApiAuthKind.API_KEY_QUERY;
    }

    @Override
    public void validate(ApiAuthConfig config, ApiSecret secret) {
        if (config.queryParam() == null) throw new IllegalArgumentException("The query parameter for the API key is missing");
        if (!secret.hasValue()) throw new IllegalArgumentException("The API key is missing");
    }

    @Override
    public void apply(ApiAuthConfig config, ApiSecret secret, ApiRequest request) {
        request.queryParam(config.queryParam(), secret.value());
    }
}
