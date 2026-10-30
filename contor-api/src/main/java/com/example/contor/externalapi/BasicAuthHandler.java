package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** BASIC: {@link ApiAuthConfig#username()} and the password (the secret), UTF-8 and Base64 (RFC 7617). */
@Component
public class BasicAuthHandler implements ApiAuthHandler {

    @Override
    public ApiAuthKind kind() {
        return ApiAuthKind.BASIC;
    }

    @Override
    public void validate(ApiAuthConfig config, ApiSecret secret) {
        if (config.username() == null) throw new IllegalArgumentException("The username is missing");
        if (!secret.hasValue()) throw new IllegalArgumentException("The password is missing");
    }

    @Override
    public void apply(ApiAuthConfig config, ApiSecret secret, ApiRequest request) {
        String credentials = config.username() + ":" + secret.value();
        request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    }
}
