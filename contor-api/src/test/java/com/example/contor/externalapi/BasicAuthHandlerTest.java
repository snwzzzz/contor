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
class BasicAuthHandlerTest {

    private final BasicAuthHandler handler = new BasicAuthHandler();

    private static final ApiAuthConfig CONFIG = new ApiAuthConfig(null, null, null, null, "Jürgen", null);
    private static final ApiSecret PASSWORD = new ApiSecret("pässword", List.of());

    @Test
    void sends_user_and_password_base64_encoded_as_utf_8() {
        var request = new ApiRequest();
        handler.validate(CONFIG, PASSWORD);
        handler.apply(CONFIG, PASSWORD, request);

        assertThat(handler.kind()).isEqualTo(ApiAuthKind.BASIC);
        assertThat(request.headers()).containsEntry("Authorization", "Basic SsO8cmdlbjpww6Rzc3dvcmQ=");
    }

    @Test
    void needs_the_username_and_the_password() {
        assertThatThrownBy(() -> handler.validate(ApiAuthConfig.NONE, PASSWORD)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
        assertThatThrownBy(() -> handler.validate(CONFIG, ApiSecret.EMPTY)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }
}
