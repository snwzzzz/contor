package com.example.contor.externalapi;

import com.example.contor.domain.ApiHeader;
import com.example.contor.domain.ApiSecret;
import com.example.contor.domain.ExternalApi;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApiSecretCodecTest {

    private final ApiSecretCodec codec = new ApiSecretCodec(JsonMapper.builder().build());

    @Test
    void writes_and_reads_a_secret() {
        var secret = new ApiSecret("key-123", List.of(new ApiHeader("X-Shop-Token", "t-1")));

        String json = codec.write(secret);

        assertThat(codec.read(api(json))).isEqualTo(secret);
    }

    @Test
    void an_empty_secret_is_stored_as_nothing() {
        assertThat(codec.write(ApiSecret.EMPTY)).isNull();
        assertThat(codec.write(new ApiSecret("", List.of()))).isNull();
        assertThat(codec.read(api(null))).isEqualTo(ApiSecret.EMPTY);
        assertThat(codec.read(api(" "))).isEqualTo(ApiSecret.EMPTY);
    }

    @Test
    void secret_headers_alone_are_kept() {
        var secret = new ApiSecret(null, List.of(new ApiHeader("X-Shop-Token", "t-1")));

        assertThat(codec.read(api(codec.write(secret)))).isEqualTo(secret);
    }

    @Test
    void unreadable_secrets_fail_without_showing_them() {
        assertThatThrownBy(() -> codec.read(api("{not json key-123"))).isInstanceOf(IllegalStateException.class)
                .message().contains("Shop").doesNotContain("key-123");
        assertThatThrownBy(() -> codec.read(api("{\"value\":\"a\\r\\nb\"}"))).isInstanceOf(IllegalStateException.class);
    }

    private static ExternalApi api(String secret) {
        return ExternalApi.builder().name("Shop").secret(secret).build();
    }
}
