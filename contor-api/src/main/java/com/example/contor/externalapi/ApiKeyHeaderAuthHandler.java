package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import org.springframework.stereotype.Component;

/** API_KEY_HEADER: the key in the header {@link ApiAuthConfig#headerName()} (e.g. {@code X-Api-Key}). */
@Component
public class ApiKeyHeaderAuthHandler implements ApiAuthHandler {

    @Override
    public ApiAuthKind kind() {
        return ApiAuthKind.API_KEY_HEADER;
    }

    @Override
    public void validate(ApiAuthConfig config, ApiSecret secret) {
        if (config.headerName() == null) throw new IllegalArgumentException("The header name for the API key is missing");
        if (!secret.hasValue()) throw new IllegalArgumentException("The API key is missing");
    }

    @Override
    public void apply(ApiAuthConfig config, ApiSecret secret, ApiRequest request) {
        request.header(config.headerName(), secret.value());
    }
}
