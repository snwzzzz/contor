package com.example.contor.ai;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The figures the model asks for before it writes a collection report: up to {@link #MAX_ANALYSES} analyses, each over
 * the records of one collection. Read leniently and still untrusted: whether the collection, fields and filters exist
 * is checked by {@code CollectionAnalysisService}, which drops what does not.
 */
public record CollectionPlan(List<Analysis> analyses) {

    public static final int MAX_ANALYSES = 3;
    public static final int MAX_LIMIT = 25;
    public static final int DEFAULT_LIMIT = 10;
    private static final int MAX_FILTERS = 5;

    /**
     * @param collection the slug of the collection whose records are analysed (lower case)
     * @param groupBy    "" = all records together (one row), {@code each} = every record on its own row, a field slug,
     *                   or {@code link:<relation type slug>}
     * @param measure    {@code count}, or {@code sum|avg|min|max:<field slug>}
     * @param ascending  smallest first (else largest first)
     * @param limit      rows to show, 1 to {@link #MAX_LIMIT}
     * @param filters    {@code field:op:value}, as in {@code GET /records}
     */
    public record Analysis(String title, String collection, String groupBy, String measure, boolean ascending, int limit,
                           List<String> filters) {}

    public CollectionPlan {
        analyses = List.copyOf(analyses);
    }

    public static CollectionPlan parse(JsonNode json) {
        List<Analysis> analyses = new ArrayList<>();
        JsonNode items = json.path("analyses");
        if (!items.isArray()) {
            return new CollectionPlan(List.of());
        }
        for (JsonNode item : items) {
            String collection = text(item, "collection").toLowerCase(Locale.ROOT);
            if (collection.isEmpty()) {
                continue;
            }
            String measure = text(item, "measure").toLowerCase(Locale.ROOT);
            int limit = item.path("limit").asInt(0);
            List<String> filters = new ArrayList<>();
            for (JsonNode filter : item.path("filters")) {
                String value = filter.asString("").strip();
                if (!value.isEmpty() && filters.size() < MAX_FILTERS) {
                    filters.add(value);
                }
            }
            analyses.add(new Analysis(text(item, "title"), collection, text(item, "groupBy").toLowerCase(Locale.ROOT),
                    measure.isEmpty() ? "count" : measure, "asc".equalsIgnoreCase(text(item, "order")),
                    limit == 0 ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT), filters));
            if (analyses.size() == MAX_ANALYSES) {
                break;
            }
        }
        return new CollectionPlan(analyses);
    }

    /** The JSON schema of the answer; {@code collections} are the slugs the model may pick from. */
    public static Map<String, Object> schema(List<String> collections) {
        return JsonSchemas.object(JsonSchemas.properties(
                "analyses", JsonSchemas.array(JsonSchemas.object(JsonSchemas.properties(
                        "title", JsonSchemas.string(),
                        "collection", JsonSchemas.oneOf(collections),
                        "groupBy", JsonSchemas.string(),
                        "measure", JsonSchemas.string(),
                        "order", JsonSchemas.oneOf(List.of("desc", "asc")),
                        "limit", JsonSchemas.integer(),
                        "filters", JsonSchemas.array(JsonSchemas.string()))))));
    }

    private static String text(JsonNode item, String name) {
        return item.path(name).asString("").strip();
    }
}
