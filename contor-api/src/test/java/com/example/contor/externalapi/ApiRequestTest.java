package com.example.contor.externalapi;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApiRequestTest {

    @Test
    void later_headers_replace_earlier_ones_whatever_the_case() {
        var request = new ApiRequest();
        request.header("accept", "text/plain");
        request.header("Accept", "application/json");

        assertThat(request.headers()).hasSize(1).containsEntry("Accept", "application/json");
    }

    @Test
    void appends_the_encoded_query_in_order() {
        var request = new ApiRequest();
        request.queryParam("q", "café & bar");
        request.queryParam("limit", "50");
        request.queryParam("q", "jobs+java");

        assertThat(request.uri(URI.create("https://api.shop.example/v1/search")))
                .isEqualTo(URI.create("https://api.shop.example/v1/search?q=jobs%2Bjava&limit=50"));
        assertThat(new ApiRequest().uri(URI.create("https://api.shop.example/v1/search")))
                .isEqualTo(URI.create("https://api.shop.example/v1/search"));
    }

    @Test
    void encodes_spaces_as_percent_20() {
        var request = new ApiRequest();
        request.queryParam("city name", "New York");

        assertThat(request.uri(URI.create("https://api.example/")).getRawQuery()).isEqualTo("city%20name=New%20York");
    }
}
