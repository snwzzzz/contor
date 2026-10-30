package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NoAuthHandlerTest {

    private final NoAuthHandler handler = new NoAuthHandler();

    @Test
    void adds_nothing() {
        var request = new ApiRequest();
        handler.validate(ApiAuthConfig.NONE, ApiSecret.EMPTY);
        handler.apply(ApiAuthConfig.NONE, ApiSecret.EMPTY, request);

        assertThat(handler.kind()).isEqualTo(ApiAuthKind.NONE);
        assertThat(request.headers()).isEmpty();
        assertThat(request.query()).isEmpty();
    }
}
