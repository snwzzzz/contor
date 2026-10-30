package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import org.springframework.stereotype.Component;

/** NONE: public APIs. Secret headers, if any, are still sent by the client. */
@Component
public class NoAuthHandler implements ApiAuthHandler {

    @Override
    public ApiAuthKind kind() {
        return ApiAuthKind.NONE;
    }

    @Override
    public void validate(ApiAuthConfig config, ApiSecret secret) {
    }

    @Override
    public void apply(ApiAuthConfig config, ApiSecret secret, ApiRequest request) {
    }
}
