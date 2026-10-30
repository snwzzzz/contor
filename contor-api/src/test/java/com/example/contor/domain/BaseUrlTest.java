package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class BaseUrlTest {

    @Test
    public void can_create_https_base_urls_without_trailing_slash_and_lower_case_host() {
        assertThat(new BaseUrl("https://API.Shop.example/v1/").value()).isEqualTo("https://api.shop.example/v1");
        assertThat(new BaseUrl("https://api.shop.example").value()).isEqualTo("https://api.shop.example");
        assertThat(new BaseUrl("https://api.shop.example:443/").value()).isEqualTo("https://api.shop.example:443");
    }

    @Test
    public void null_and_empty_base_url_throws_error() {
        assertThatThrownBy(() -> new BaseUrl(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseUrl("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void only_https_on_the_default_port_without_user_info_query_or_fragment() {
        assertThatThrownBy(() -> new BaseUrl("http://api.shop.example")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseUrl("https://api.shop.example:8443")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseUrl("https://user:pw@api.shop.example")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseUrl("https://api.shop.example/v1?key=1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseUrl("https://api.shop.example/v1#top")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseUrl("file:///etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseUrl("not a url")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BaseUrl("https://api.shop.example/" + "a".repeat(2000))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void resolves_an_endpoint_path_below_the_base_path() {
        assertThat(new BaseUrl("https://api.shop.example/v1").resolve(new ApiPath("/orders")))
                .isEqualTo(URI.create("https://api.shop.example/v1/orders"));
        assertThat(new BaseUrl("https://api.shop.example").resolve(new ApiPath("/")))
                .isEqualTo(URI.create("https://api.shop.example/"));
    }
}
