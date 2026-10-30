package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ApiPathTest {

    @Test
    public void can_create_relative_paths() {
        assertThat(new ApiPath("/").value()).isEqualTo("/");
        assertThat(new ApiPath("/v2/orders").value()).isEqualTo("/v2/orders");
        assertThat(new ApiPath("/jobs/search:latest").value()).isEqualTo("/jobs/search:latest");
        assertThat(new ApiPath("/caf%C3%A9/items.json").value()).isEqualTo("/caf%C3%A9/items.json");
    }

    @Test
    public void null_and_empty_path_throws_error() {
        assertThatThrownBy(() -> new ApiPath(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void paths_that_could_leave_the_base_url_are_rejected() {
        assertThatThrownBy(() -> new ApiPath("orders")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("//evil.example/orders")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("https://evil.example/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/v1/../admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/v1/..")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/v1/./orders")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/v1/%2e%2e/admin")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void query_fragment_spaces_and_bad_escapes_are_rejected() {
        assertThatThrownBy(() -> new ApiPath("/orders?status=open")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/orders#top")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/my orders")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/orders%zz")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/orders\r\nHost: evil")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiPath("/" + "a".repeat(1000))).isInstanceOf(IllegalArgumentException.class);
    }
}
