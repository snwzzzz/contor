package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ApiAuthConfigTest {

    @Test
    public void every_setting_is_optional() {
        assertThat(ApiAuthConfig.NONE.headerName()).isNull();
        assertThat(new ApiAuthConfig("X-Api-Key", "api_key", "https://auth.shop.example/token", "orders.read", "me", "contor-client"))
                .extracting(ApiAuthConfig::headerName, ApiAuthConfig::queryParam, ApiAuthConfig::tokenUrl, ApiAuthConfig::clientId)
                .containsExactly("X-Api-Key", "api_key", "https://auth.shop.example/token", "contor-client");
    }

    @Test
    public void invalid_header_names_query_params_and_token_urls_throw_error() {
        assertThatThrownBy(() -> new ApiAuthConfig("X Api Key", null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiAuthConfig("Host", null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiAuthConfig(null, "api key", null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiAuthConfig(null, "", null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiAuthConfig(null, null, "http://auth.shop.example/token", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiAuthConfig(null, null, null, "a\r\nb", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiAuthConfig(null, null, null, null, "me:you", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiAuthConfig(null, null, null, null, null, "client\nid")).isInstanceOf(IllegalArgumentException.class);
    }
}
