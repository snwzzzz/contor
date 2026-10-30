package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class HttpsUrlTest {

    @Test
    public void can_create_https_urls_with_path_query_and_fragment() {
        assertThat(new HttpsUrl("https://docs.shop.example/api?version=2#orders").value())
                .isEqualTo("https://docs.shop.example/api?version=2#orders");
    }

    @Test
    public void null_and_empty_url_throws_error() {
        assertThatThrownBy(() -> new HttpsUrl(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpsUrl("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void only_https_on_the_default_port_without_user_info() {
        assertThatThrownBy(() -> new HttpsUrl("http://docs.shop.example")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpsUrl("https://docs.shop.example:8080/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpsUrl("https://me@docs.shop.example/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpsUrl("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpsUrl("https://docs.shop.example/" + "a".repeat(2000))).isInstanceOf(IllegalArgumentException.class);
    }
}
