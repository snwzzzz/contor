package com.example.contor.externalapi;

import com.example.contor.domain.ApiPath;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.EndpointSource;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DiscoveredEndpointTest {

    private static final BaseUrl SHOP = new BaseUrl("https://api.shop.example/v1");

    private static DiscoveredEndpoint endpoint(String name, String path, Map<String, String> query) {
        return new DiscoveredEndpoint(name, new ApiPath(path), query, null, null, null, EndpointSource.MODEL);
    }

    @Test
    void a_path_is_kept_relative_to_the_base_url() {
        assertThat(DiscoveredEndpoint.relativePath("/orders", SHOP)).hasValue(new ApiPath("/orders"));
        assertThat(DiscoveredEndpoint.relativePath("orders", SHOP)).hasValue(new ApiPath("/orders"));
        assertThat(DiscoveredEndpoint.relativePath("https://api.shop.example/v1/orders", SHOP)).hasValue(new ApiPath("/orders"));
        assertThat(DiscoveredEndpoint.relativePath("https://API.shop.example/v1/orders/", SHOP)).hasValue(new ApiPath("/orders/"));
    }

    @Test
    void foreign_hosts_traversal_templates_and_queries_are_dropped() {
        for (String path : List.of("https://evil.example/v1/orders", "https://api.shop.example/v2/orders", "//evil.example/x",
                "/a/../b", "/orders/{id}", "/orders/:id", "/orders?status=open", "", " ")) {
            assertThat(DiscoveredEndpoint.relativePath(path, SHOP)).as(path).isEqualTo(Optional.empty());
        }
    }

    @Test
    void distinct_drops_repeats_names_the_rest_uniquely_and_keeps_thirty() {
        var endpoints = new ArrayList<DiscoveredEndpoint>();
        endpoints.add(endpoint("Orders", "/orders", Map.of()));
        endpoints.add(endpoint("orders", "/orders", Map.of()));
        endpoints.add(endpoint("orders", "/orders", Map.of("status", "open")));
        for (int i = 0; i < 40; i++) endpoints.add(endpoint("p", "/p" + i, Map.of()));

        var distinct = DiscoveredEndpoint.distinct(endpoints);

        assertThat(distinct).hasSize(DiscoveredEndpoint.MAX_ENDPOINTS);
        assertThat(distinct).extracting(DiscoveredEndpoint::name).startsWith("Orders", "orders (2)", "p");
        assertThat(distinct.get(1).query()).containsEntry("status", "open");
    }
}
