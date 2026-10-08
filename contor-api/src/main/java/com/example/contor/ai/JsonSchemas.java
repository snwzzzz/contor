package com.example.contor.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON schemas passed to Ollama as {@code format} (structured outputs). Ollama turns them into a grammar,
 * so the model can only answer with matching JSON; values are still validated by the caller.
 */
public final class JsonSchemas {

    private JsonSchemas() {
    }

    /** An object with the given properties, all of them required. */
    public static Map<String, Object> object(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        return schema;
    }

    /** Ordered property map: {@code properties("name", string(), "age", integer())}. */
    public static Map<String, Object> properties(Object... namesAndSchemas) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < namesAndSchemas.length; i += 2) {
            properties.put((String) namesAndSchemas[i], namesAndSchemas[i + 1]);
        }
        return properties;
    }

    /** An object whose keys are free (e.g. record values keyed by field slug). */
    public static Map<String, Object> freeObject() {
        return Map.of("type", "object");
    }

    public static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    public static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    public static Map<String, Object> integer() {
        return Map.of("type", "integer");
    }

    public static Map<String, Object> number() {
        return Map.of("type", "number");
    }

    public static Map<String, Object> oneOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }
}
