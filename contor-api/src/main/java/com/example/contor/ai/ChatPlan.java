package com.example.contor.ai;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a chat question is about, as the model sees it before the app looks anything up: up to {@link #MAX_SEARCHES}
 * search strings (names, numbers, distinctive words the records contain) and up to {@link #MAX_COLLECTIONS} collections
 * whose newest records the question needs ("which complaints are still open?"). Read leniently and still untrusted:
 * unknown collections are dropped here, the searches only ever go into a parameterized records query.
 */
public record ChatPlan(List<String> searches, List<String> collections) {

    public static final int MAX_SEARCHES = 5;
    public static final int MAX_COLLECTIONS = 2;
    public static final int MAX_SEARCH_LENGTH = 100;

    public ChatPlan {
        searches = List.copyOf(searches);
        collections = List.copyOf(collections);
    }

    public boolean isEmpty() {
        return searches.isEmpty() && collections.isEmpty();
    }

    /** @param slugs the collections that exist; others are dropped */
    public static ChatPlan parse(JsonNode json, List<String> slugs) {
        List<String> searches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : json.path("searches")) {
            String search = item.asString("").strip();
            if (!search.isEmpty() && search.length() <= MAX_SEARCH_LENGTH && seen.add(search.toLowerCase(Locale.ROOT))
                    && searches.size() < MAX_SEARCHES) {
                searches.add(search);
            }
        }
        List<String> collections = new ArrayList<>();
        for (JsonNode item : json.path("collections")) {
            String slug = item.asString("").strip().toLowerCase(Locale.ROOT);
            if (slugs.contains(slug) && !collections.contains(slug) && collections.size() < MAX_COLLECTIONS) {
                collections.add(slug);
            }
        }
        return new ChatPlan(searches, collections);
    }

    /** The JSON schema of the answer; {@code slugs} are the collections the model may pick from. */
    public static Map<String, Object> schema(List<String> slugs) {
        return JsonSchemas.object(JsonSchemas.properties(
                "searches", JsonSchemas.array(JsonSchemas.string()),
                "collections", JsonSchemas.array(slugs.isEmpty() ? JsonSchemas.string() : JsonSchemas.oneOf(slugs))));
    }
}
