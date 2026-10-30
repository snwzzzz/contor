package com.example.contor.externalapi;

import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiSecret;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BearerAuthHandlerTest {

    private static final ApiSecret KEY = new ApiSecret("key-123", List.of());
    private final BearerAuthHandler handler = new BearerAuthHandler();

    @Test
    void sends_the_token_in_the_authorization_header() {
        var request = new ApiRequest();
        handler.validate(ApiAuthConfig.NONE, KEY);
        handler.apply(ApiAuthConfig.NONE, KEY, request);

        assertThat(handler.kind()).isEqualTo(ApiAuthKind.BEARER);
        assertThat(request.headers()).containsEntry("Authorization", "Bearer key-123");
    }

    @Test
    void needs_the_token() {
        assertThatThrownBy(() -> handler.validate(ApiAuthConfig.NONE, ApiSecret.EMPTY)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }
}
