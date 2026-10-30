package com.example.contor.externalapi;

import com.example.contor.domain.JsonPathExpr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The items of an external API's JSON answer: the nodes under the endpoint's items path (the whole answer without one),
 * each with its key (the value under the key path, else its content hash) and the hash of its content. Paths use the
 * {@link JsonPathExpr} subset.
 */
@RequiredArgsConstructor
@Component
public class JsonItems {

    /** Items read from one answer at most; the rest are ignored. */
    public static final int MAX_ITEMS = 1000;
    /** The key of the whole answer when it is one item. */
    static final String WHOLE_ANSWER = "$";
    private static final int MAX_KEY_LENGTH = 500;
    private static final Pattern STEP = Pattern.compile("\\.([A-Za-z_][A-Za-z0-9_-]*)|\\[(\\*|\\d+)]");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * @param key  identifies the item across calls
     * @param hash SHA-256 of its JSON (key order counts)
     * @param data the item; a value that is no object is {@code {"value": …}}
     */
    public record Item(String key, String hash, Map<String, Object> data) {
    }

    /** The items of {@code body}, at most {@link #MAX_ITEMS}, the first of several with one key; empty when it is no JSON. */
    public Optional<List<Item>> extract(String body, JsonPathExpr itemsPath, JsonPathExpr itemKeyPath) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (root == null || root.isMissingNode()) return Optional.empty();

        List<JsonNode> nodes = itemsPath != null ? select(root, itemsPath) : List.of(root);
        Map<String, Item> items = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            if (items.size() == MAX_ITEMS) break;
            String hash = hash(node.toString());
            String key = key(node, itemsPath == null, itemKeyPath, hash);
            items.putIfAbsent(key, new Item(key, hash, data(node)));
        }
        return Optional.of(List.copyOf(items.values()));
    }

    /** The first value {@code path} selects in an item's {@code data}; empty for nothing, a list or an object. */
    public Optional<Object> value(Map<String, Object> data, JsonPathExpr path) {
        List<JsonNode> found = select(objectMapper.valueToTree(data), path);
        if (found.isEmpty() || !found.getFirst().isValueNode() || found.getFirst().isNull()) return Optional.empty();
        return Optional.ofNullable(objectMapper.convertValue(found.getFirst(), Object.class));
    }

    /**
     * Whether {@code itemsPath} finds its place in {@code body}: for a path ending in {@code [*]} a list there (an empty
     * list fits: no items right now), else any value. A path that does not fit is most likely wrong for this answer.
     */
    public boolean fits(String body, JsonPathExpr itemsPath) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException e) {
            return false;
        }
        if (root == null || root.isMissingNode()) return false;
        String path = itemsPath.value();
        if (!path.endsWith("[*]")) return !select(root, itemsPath).isEmpty();
        return select(root, new JsonPathExpr(path.substring(0, path.length() - 3))).stream().anyMatch(JsonNode::isArray);
    }

    /** SHA-256 of {@code text} (UTF-8) as lower-case hex. */
    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String key(JsonNode node, boolean wholeAnswer, JsonPathExpr itemKeyPath, String hash) {
        if (itemKeyPath != null) {
            List<JsonNode> found = select(node, itemKeyPath);
            if (!found.isEmpty() && found.getFirst().isValueNode() && !found.getFirst().isNull()) {
                String key = found.getFirst().asString();
                if (!key.isEmpty() && key.length() <= MAX_KEY_LENGTH) return key;
            }
        }
        return wholeAnswer ? WHOLE_ANSWER : hash;
    }

    private Map<String, Object> data(JsonNode node) {
        if (node.isObject()) return objectMapper.convertValue(node, MAP);
        Map<String, Object> wrapped = new HashMap<>();
        wrapped.put("value", objectMapper.convertValue(node, Object.class));
        return wrapped;
    }

    /** The nodes {@code path} selects below {@code root}; missing members and indexes select nothing. */
    private static List<JsonNode> select(JsonNode root, JsonPathExpr path) {
        List<JsonNode> current = List.of(root);
        Matcher step = STEP.matcher(path.value());
        int at = 1;
        while (at < path.value().length() && step.find(at)) {
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode node : current) {
                if (step.group(1) != null) {
                    JsonNode child = node.isObject() ? node.get(step.group(1)) : null;
                    if (child != null) next.add(child);
                } else if (node.isArray()) {
                    if ("*".equals(step.group(2))) {
                        for (int i = 0; i < node.size(); i++) next.add(node.get(i));
                    } else {
                        int index = Integer.parseInt(step.group(2));
                        if (index < node.size()) next.add(node.get(index));
                    }
                }
            }
            current = next;
            at = step.end();
        }
        return current;
    }
}
