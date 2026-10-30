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
class ApiKeyHeaderAuthHandlerTest {

    private static final ApiSecret KEY = new ApiSecret("key-123", List.of());
    private final ApiKeyHeaderAuthHandler handler = new ApiKeyHeaderAuthHandler();

    private static final ApiAuthConfig CONFIG = new ApiAuthConfig("X-Api-Key", null, null, null, null, null);

    @Test
    void sends_the_key_in_the_configured_header() {
        var request = new ApiRequest();
        handler.validate(CONFIG, KEY);
        handler.apply(CONFIG, KEY, request);

        assertThat(handler.kind()).isEqualTo(ApiAuthKind.API_KEY_HEADER);
        assertThat(request.headers()).containsEntry("X-Api-Key", "key-123");
    }

    @Test
    void needs_the_header_name_and_the_key() {
        assertThatThrownBy(() -> handler.validate(ApiAuthConfig.NONE, KEY)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header name");
        assertThatThrownBy(() -> handler.validate(CONFIG, ApiSecret.EMPTY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("API key");
    }

    @Test
    void validation_messages_never_contain_the_key() {
        assertThatThrownBy(() -> handler.validate(ApiAuthConfig.NONE, KEY)).message().doesNotContain("key-123");
    }
}
