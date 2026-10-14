package com.example.contor.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON schemas passed to Ollama as {@code format} (structured outputs). Ollama turns them into a grammar,
 * so the model can only answer with matching JSON; values are still validated by the caller. Every map keeps insertion
 * order (never {@code Map.of}, whose order changes between JVMs), so the same call sends the same bytes in every run.
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
        return ordered(namesAndSchemas);
    }

    /** An object whose keys are free (e.g. record values keyed by field slug). */
    public static Map<String, Object> freeObject() {
        return ordered("type", "object");
    }

    public static Map<String, Object> array(Map<String, Object> items) {
        return ordered("type", "array", "items", items);
    }

    public static Map<String, Object> string() {
        return ordered("type", "string");
    }

    public static Map<String, Object> integer() {
        return ordered("type", "integer");
    }

    public static Map<String, Object> bool() {
        return ordered("type", "boolean");
    }

    public static Map<String, Object> number() {
        return ordered("type", "number");
    }

    public static Map<String, Object> oneOf(List<String> values) {
        return ordered("type", "string", "enum", values);
    }

    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
