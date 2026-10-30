package com.example.contor.externalapi;

import com.example.contor.domain.JsonPathExpr;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JsonItemsTest {

    private final JsonItems items = new JsonItems(JsonMapper.builder().build());

    private static JsonPathExpr path(String value) {
        return new JsonPathExpr(value);
    }

    private static List<String> keys(Optional<List<JsonItems.Item>> found) {
        return found.orElseThrow().stream().map(JsonItems.Item::key).toList();
    }

    @Test
    void extracts_the_items_under_the_path_with_their_key() {
        var found = items.extract("{\"data\":{\"orders\":[{\"id\":1,\"total\":3},{\"id\":\"a-2\",\"total\":4}]}}",
                path("$.data.orders[*]"), path("$.id"));

        assertThat(keys(found)).containsExactly("1", "a-2");
        assertThat(found.orElseThrow().getFirst().data()).isEqualTo(Map.of("id", 1, "total", 3));
    }

    @Test
    void reads_indexes_and_nested_keys() {
        var found = items.extract("{\"pages\":[{\"rows\":[{\"ref\":{\"no\":7}}]},{\"rows\":[{\"ref\":{\"no\":8}}]}]}",
                path("$.pages[1].rows[*]"), path("$.ref.no"));

        assertThat(keys(found)).containsExactly("8");
    }

    @Test
    void flattens_every_element_of_nested_lists() {
        var found = items.extract("[{\"rows\":[{\"id\":1},{\"id\":2}]},{\"rows\":[{\"id\":3}]}]", path("$[*].rows[*]"), path("$.id"));

        assertThat(keys(found)).containsExactly("1", "2", "3");
    }

    @Test
    void the_whole_answer_is_one_item_without_an_items_path() {
        var found = items.extract("{\"temperature\":21}", null, null);

        assertThat(keys(found)).containsExactly("$");
        assertThat(found.orElseThrow().getFirst().data()).isEqualTo(Map.of("temperature", 21));
    }

    @Test
    void items_without_a_usable_key_are_identified_by_their_content() {
        var found = items.extract("[{\"name\":\"x\"},{\"name\":\"y\",\"id\":{\"nested\":true}}]", path("$[*]"), path("$.id"));

        assertThat(found.orElseThrow()).extracting(JsonItems.Item::key)
                .allSatisfy(key -> assertThat(key).hasSize(64))
                .doesNotHaveDuplicates();
    }

    @Test
    void wraps_values_that_are_no_objects() {
        var found = items.extract("{\"tags\":[\"a\",\"b\"]}", path("$.tags[*]"), null);

        assertThat(found.orElseThrow()).extracting(JsonItems.Item::data)
                .containsExactly(Map.of("value", "a"), Map.of("value", "b"));
    }

    @Test
    void keeps_the_first_of_items_with_the_same_key() {
        var found = items.extract("[{\"id\":1,\"v\":1},{\"id\":1,\"v\":2}]", path("$[*]"), path("$.id"));

        assertThat(found.orElseThrow()).singleElement().extracting(JsonItems.Item::data).isEqualTo(Map.of("id", 1, "v", 1));
    }

    @Test
    void finds_nothing_under_a_missing_path() {
        assertThat(items.extract("{\"data\":{}}", path("$.data.orders[*]"), path("$.id"))).hasValue(List.of());
    }

    @Test
    void reads_at_most_the_item_limit() {
        String body = IntStream.range(0, JsonItems.MAX_ITEMS + 5).mapToObj(i -> "{\"id\":" + i + "}")
                .collect(Collectors.joining(",", "[", "]"));

        assertThat(items.extract(body, path("$[*]"), path("$.id")).orElseThrow()).hasSize(JsonItems.MAX_ITEMS);
    }

    @Test
    void is_empty_when_the_answer_is_no_json() {
        assertThat(items.extract("<html></html>", path("$[*]"), null)).isEmpty();
    }

    @Test
    void the_hash_changes_with_the_content() {
        var first = items.extract("[{\"id\":1,\"v\":1}]", path("$[*]"), path("$.id")).orElseThrow().getFirst();
        var same = items.extract("[{\"id\":1,\"v\":1}]", path("$[*]"), path("$.id")).orElseThrow().getFirst();
        var changed = items.extract("[{\"id\":1,\"v\":2}]", path("$[*]"), path("$.id")).orElseThrow().getFirst();

        assertThat(first.hash()).isEqualTo(same.hash()).isNotEqualTo(changed.hash()).hasSize(64);
        assertThat(JsonItems.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void reads_one_value_of_an_item() {
        Map<String, Object> order = Map.of("id", 1, "customer", Map.of("name", "Anna"), "lines", List.of(Map.of("sku", "A-1")), "note", "");

        assertThat(items.value(order, path("$.customer.name"))).hasValue("Anna");
        assertThat(items.value(order, path("$.id"))).hasValue(1);
        assertThat(items.value(order, path("$.lines[0].sku"))).hasValue("A-1");
        assertThat(items.value(order, path("$.lines"))).as("a list is no value").isEmpty();
        assertThat(items.value(order, path("$.customer"))).as("an object is no value").isEmpty();
        assertThat(items.value(order, path("$.missing"))).isEmpty();
        assertThat(items.value(Map.of("value", "x"), path("$.value"))).hasValue("x");
    }

    @Test
    void tells_whether_an_items_path_fits_the_answer() {
        assertThat(items.fits("{\"results\":[]}", path("$.results[*]"))).isTrue();
        assertThat(items.fits("{\"results\":[{\"id\":1}]}", path("$.results[*]"))).isTrue();
        assertThat(items.fits("{\"results\":[{\"id\":1}]}", path("$[*]"))).as("an object is no list").isFalse();
        assertThat(items.fits("{\"data\":{}}", path("$.data.orders[*]"))).isFalse();
        assertThat(items.fits("{\"order\":{\"id\":1}}", path("$.order"))).isTrue();
        assertThat(items.fits("<html>", path("$[*]"))).isFalse();
    }
}
