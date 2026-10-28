package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ApiHeaderTest {

    @Test
    public void can_create_a_header() {
        var header = new ApiHeader("X-Shop-Version", "2024-10");
        assertThat(header.name()).isEqualTo("X-Shop-Version");
        assertThat(header.value()).isEqualTo("2024-10");
        assertThat(new ApiHeader("Accept", "").value()).isEmpty();
    }

    @Test
    public void null_and_empty_names_and_null_values_throw_error() {
        assertThatThrownBy(() -> new ApiHeader(null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiHeader("", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiHeader("Accept", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void invalid_names_and_values_that_could_inject_headers_are_rejected() {
        assertThatThrownBy(() -> new ApiHeader("X Shop", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiHeader("X-Shop:", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiHeader("X-Shop", "a\r\nHost: evil.example")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiHeader("X-Shop", "a\u0000b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiHeader("X-Shop", "a".repeat(4001))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiHeader("X-" + "a".repeat(100), "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void headers_the_http_client_controls_are_rejected_in_any_case() {
        for (String name : new String[] {"Host", "content-length", "Connection", "Transfer-Encoding", "Upgrade", "Expect", "TE"}) {
            assertThatThrownBy(() -> new ApiHeader(name, "x")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
