package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ApiSecretTest {

    @Test
    public void holds_a_secret_value_and_secret_headers() {
        var secret = new ApiSecret("key-123", List.of(new ApiHeader("X-Shop-Token", "t-1")));
        assertThat(secret.hasValue()).isTrue();
        assertThat(secret.headers()).containsExactly(new ApiHeader("X-Shop-Token", "t-1"));
    }

    @Test
    public void both_parts_are_optional() {
        assertThat(ApiSecret.EMPTY.hasValue()).isFalse();
        assertThat(new ApiSecret("", null).hasValue()).isFalse();
        assertThat(new ApiSecret(null, null).headers()).isEmpty();
    }

    @Test
    public void values_that_could_inject_headers_throw_error() {
        assertThatThrownBy(() -> new ApiSecret("key\r\nHost: evil", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiSecret("a".repeat(8001), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void never_shows_the_value() {
        assertThat(new ApiSecret("key-123", List.of(new ApiHeader("X-Shop-Token", "t-1"))).toString())
                .doesNotContain("key-123").doesNotContain("t-1");
    }
}
