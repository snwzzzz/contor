package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schema is part of every model request: its JSON must be the same in every JVM (no {@code Map.of}, whose iteration
 * order changes between runs), so identical calls are byte-identical and the eval's answer cache can recognize them.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JsonSchemasTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void every_schema_serializes_with_type_first_in_a_fixed_order() {
        assertThat(mapper.writeValueAsString(JsonSchemas.array(JsonSchemas.string())))
                .isEqualTo("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}");
        assertThat(mapper.writeValueAsString(JsonSchemas.oneOf(List.of("a", "b"))))
                .isEqualTo("{\"type\":\"string\",\"enum\":[\"a\",\"b\"]}");
        assertThat(mapper.writeValueAsString(JsonSchemas.object(JsonSchemas.properties("n", JsonSchemas.integer(), "f", JsonSchemas.bool(),
                "x", JsonSchemas.number(), "o", JsonSchemas.freeObject()))))
                .isEqualTo("{\"type\":\"object\",\"properties\":{\"n\":{\"type\":\"integer\"},\"f\":{\"type\":\"boolean\"},"
                        + "\"x\":{\"type\":\"number\"},\"o\":{\"type\":\"object\"}},\"required\":[\"n\",\"f\",\"x\",\"o\"]}");
    }

    @Test
    void schema_maps_keep_insertion_order() {
        assertThat(List.copyOf(JsonSchemas.array(JsonSchemas.string()).keySet())).containsExactly("type", "items");
        assertThat(List.copyOf(JsonSchemas.oneOf(List.of("a")).keySet())).containsExactly("type", "enum");
        assertThat(JsonSchemas.array(JsonSchemas.string()).getClass().getName()).doesNotContain("ImmutableCollections");
    }
}
