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
class ApiKeyQueryAuthHandlerTest {

    private static final ApiSecret KEY = new ApiSecret("key-123", List.of());
    private final ApiKeyQueryAuthHandler handler = new ApiKeyQueryAuthHandler();

    private static final ApiAuthConfig CONFIG = new ApiAuthConfig(null, "api_key", null, null, null, null);

    @Test
    void sends_the_key_as_query_parameter() {
        var request = new ApiRequest();
        handler.validate(CONFIG, KEY);
        handler.apply(CONFIG, KEY, request);

        assertThat(handler.kind()).isEqualTo(ApiAuthKind.API_KEY_QUERY);
        assertThat(request.query()).containsEntry("api_key", "key-123");
    }

    @Test
    void needs_the_query_parameter_and_the_key() {
        assertThatThrownBy(() -> handler.validate(ApiAuthConfig.NONE, KEY)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query parameter");
        assertThatThrownBy(() -> handler.validate(CONFIG, ApiSecret.EMPTY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("API key");
    }
}
