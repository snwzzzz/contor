package com.example.contor.externalapi;

import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.EndpointSource;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OpenApiReaderTest {

    private static final BaseUrl SHOP = new BaseUrl("https://api.shop.example/v1");

    private final OpenApiReader reader = new OpenApiReader(JsonMapper.builder().build());

    private static List<String> paths(List<DiscoveredEndpoint> endpoints) {
        return endpoints.stream().map(endpoint -> endpoint.path().value()).toList();
    }

    @Test
    void reads_the_get_endpoints_of_an_openapi_3_document_with_their_items() {
        String spec = """
                {"openapi": "3.0.3", "info": {"title": "Shop", "version": "1"},
                 "servers": [{"url": "https://api.shop.example/v1"}],
                 "paths": {
                   "/orders": {
                     "get": {"summary": "List orders", "description": "Orders, newest first",
                             "responses": {"200": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/OrderList"}}}}}},
                     "post": {"summary": "Place an order", "responses": {"201": {}}}},
                   "/orders/{id}": {"get": {"summary": "One order", "responses": {"200": {}}}},
                   "/status": {"get": {"operationId": "getStatus", "responses": {"200": {"description": "ok"}}}}
                 },
                 "components": {"schemas": {
                   "OrderList": {"type": "object", "properties": {"meta": {"type": "object"},
                                 "data": {"type": "array", "items": {"$ref": "#/components/schemas/Order"}}}},
                   "Order": {"type": "object", "properties": {"id": {"type": "string"}, "total": {"type": "number"}}}}}}
                """;

        var endpoints = reader.read(spec, SHOP).orElseThrow();

        assertThat(paths(endpoints)).containsExactly("/orders", "/status");
        var orders = endpoints.getFirst();
        assertThat(orders.name()).isEqualTo("List orders");
        assertThat(orders.description()).isEqualTo("Orders, newest first");
        assertThat(orders.itemsPath().value()).isEqualTo("$.data[*]");
        assertThat(orders.itemKeyPath().value()).isEqualTo("$.id");
        assertThat(orders.source()).isEqualTo(EndpointSource.DOCS);
        assertThat(endpoints.get(1).name()).isEqualTo("getStatus");
        assertThat(endpoints.get(1).itemsPath()).isNull();
    }

    @Test
    void reads_a_swagger_2_document_in_yaml_below_the_base_url() {
        String spec = """
                swagger: "2.0"
                info: {title: Shop, version: "1"}
                host: shop.example
                basePath: /api/v2
                paths:
                  /products:
                    get:
                      responses:
                        "200":
                          schema:
                            type: array
                            items: {$ref: "#/definitions/Product"}
                definitions:
                  Product:
                    type: object
                    properties:
                      sku: {type: string}
                """;

        var endpoints = reader.read(spec, new BaseUrl("https://shop.example/api")).orElseThrow();

        assertThat(endpoints).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.path().value()).isEqualTo("/v2/products");
            assertThat(endpoint.name()).isEqualTo("GET /v2/products");
            assertThat(endpoint.itemsPath().value()).isEqualTo("$[*]");
            assertThat(endpoint.itemKeyPath().value()).isEqualTo("$.sku");
        });
    }

    @Test
    void fills_required_query_parameters_from_examples_and_skips_operations_it_cannot_fill() {
        String spec = """
                {"openapi": "3.1.0", "info": {"title": "Jobs", "version": "1"},
                 "components": {"parameters": {"Limit": {"name": "limit", "in": "query", "required": true, "schema": {"default": 20}}}},
                 "paths": {
                   "/jobs": {"get": {"parameters": [
                       {"name": "country", "in": "query", "required": true, "example": "AT"},
                       {"$ref": "#/components/parameters/Limit"},
                       {"name": "page", "in": "query"}], "responses": {"200": {}}}},
                   "/search": {"get": {"parameters": [{"name": "q", "in": "query", "required": true}], "responses": {"200": {}}}},
                   "/regions": {"parameters": [{"name": "kind", "in": "query", "required": true, "schema": {"enum": ["state", "city"]}}],
                                "get": {"responses": {"200": {}}}}
                 }}
                """;

        var endpoints = reader.read(spec, new BaseUrl("https://jobs.example")).orElseThrow();

        assertThat(paths(endpoints)).containsExactly("/jobs", "/regions");
        assertThat(endpoints.getFirst().query()).containsExactly(Map.entry("country", "AT"), Map.entry("limit", "20"));
        assertThat(endpoints.get(1).query()).containsExactly(Map.entry("kind", "state"));
    }

    @Test
    void ignores_deprecated_operations_and_a_server_on_another_path() {
        String spec = """
                {"openapi": "3.0.0", "info": {"title": "x", "version": "1"},
                 "servers": [{"url": "https://other.example/{version}"}],
                 "paths": {"/a": {"get": {"deprecated": true, "responses": {}}}, "/b": {"get": {"responses": {}}}}}
                """;

        assertThat(paths(reader.read(spec, SHOP).orElseThrow())).containsExactly("/b");
    }

    @Test
    void keeps_at_most_thirty_endpoints_with_distinct_names() {
        String paths = IntStream.range(0, 40).mapToObj(i -> "\"/p" + i + "\": {\"get\": {\"summary\": \"List\"}}")
                .collect(Collectors.joining(","));
        String spec = "{\"openapi\": \"3.0.0\", \"info\": {\"title\": \"x\", \"version\": \"1\"}, \"paths\": {" + paths + "}}";

        var endpoints = reader.read(spec, SHOP).orElseThrow();

        assertThat(endpoints).hasSize(DiscoveredEndpoint.MAX_ENDPOINTS);
        assertThat(endpoints).extracting(DiscoveredEndpoint::name).startsWith("List", "List (2)", "List (3)").doesNotHaveDuplicates();
    }

    @Test
    void is_empty_for_anything_that_is_no_openapi_document() {
        assertThat(reader.read("<html><body>Docs</body></html>", SHOP)).isEmpty();
        assertThat(reader.read("{\"data\": []}", SHOP)).isEmpty();
        assertThat(reader.read("just: [unclosed", SHOP)).isEmpty();
        assertThat(reader.read("{\"openapi\": \"3.0.0\"}", SHOP)).hasValue(List.of());
    }

    @Test
    void refuses_yaml_that_expands_aliases_without_end() {
        String bomb = "a: &a [x, x, x, x, x, x, x, x, x]\n" + IntStream.range(0, 60)
                .mapToObj(i -> "b" + i + ": &b" + i + " [*" + (i == 0 ? "a" : "b" + (i - 1)) + ", *" + (i == 0 ? "a" : "b" + (i - 1)) + "]")
                .collect(Collectors.joining("\n")) + "\nopenapi: 3.0.0\n";

        assertThat(reader.read(bomb, SHOP)).isEmpty();
    }
}
