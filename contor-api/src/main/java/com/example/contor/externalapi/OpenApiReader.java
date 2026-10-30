package com.example.contor.externalapi;

import com.example.contor.domain.ApiPath;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.EndpointSource;
import com.example.contor.domain.JsonPathExpr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads the endpoints worth polling from an OpenAPI 3 or Swagger 2 document (JSON or YAML), in code, without the
 * model: every GET operation without path parameters, not deprecated, whose required query parameters have an
 * example, default or enum value. The items path and key come from the 200 response's schema (a list, or the first list
 * property of an object; a key named like {@code id}). Paths are made relative to the app's base URL using the
 * document's server URL or base path.
 */
@RequiredArgsConstructor
@Component
public class OpenApiReader {

    private static final Pattern PLAIN_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_-]*$");
    private static final List<String> ID_KEYS = List.of("id", "uuid", "key", "sku", "code", "slug");
    /** YAML aliases a document may use; more is a "billion laughs" expansion, not an API description. */
    private static final int MAX_ALIASES = 50;
    private static final int MAX_REF_DEPTH = 10;

    private final ObjectMapper objectMapper;

    /** The endpoints of {@code document}; empty when it is no OpenAPI 3 or Swagger 2 document. */
    public Optional<List<DiscoveredEndpoint>> read(String document, BaseUrl base) {
        JsonNode root = parse(document);
        if (root == null || !root.isObject()) return Optional.empty();
        boolean openApi3 = root.path("openapi").asString("").startsWith("3");
        boolean swagger2 = root.path("swagger").asString("").startsWith("2");
        if (!openApi3 && !swagger2) return Optional.empty();

        String prefix = prefix(openApi3 ? serverPath(root) : root.path("basePath").asString(""), base);
        List<DiscoveredEndpoint> endpoints = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : root.path("paths").properties()) {
            JsonNode get = path.getValue().path("get");
            if (!get.isObject() || get.path("deprecated").asBoolean(false)) continue;
            Optional<ApiPath> apiPath = DiscoveredEndpoint.relativePath(prefix + path.getKey(), base);
            Optional<Map<String, String>> query = query(root, path.getValue().path("parameters"), get.path("parameters"));
            if (apiPath.isEmpty() || query.isEmpty()) continue;

            JsonNode schema = resolve(root, responseSchema(get, openApi3));
            JsonPathExpr itemsPath = null;
            JsonPathExpr itemKeyPath = null;
            JsonNode itemSchema = null;
            if (isList(schema)) {
                itemsPath = new JsonPathExpr("$[*]");
                itemSchema = schema.path("items");
            } else {
                for (Map.Entry<String, JsonNode> property : schema.path("properties").properties()) {
                    JsonNode value = resolve(root, property.getValue());
                    if (isList(value) && PLAIN_KEY.matcher(property.getKey()).matches()) {
                        itemsPath = new JsonPathExpr("$." + property.getKey() + "[*]");
                        itemSchema = value.path("items");
                        break;
                    }
                }
            }
            if (itemSchema != null) {
                JsonNode properties = resolve(root, itemSchema).path("properties");
                itemKeyPath = ID_KEYS.stream().filter(properties::has).findFirst().map(key -> new JsonPathExpr("$." + key)).orElse(null);
            }

            String name = DiscoveredEndpoint.clip(firstText(get.path("summary"), get.path("operationId")), DiscoveredEndpoint.MAX_NAME);
            endpoints.add(new DiscoveredEndpoint(name != null ? name : "GET " + apiPath.get().value(), apiPath.get(), query.get(),
                    itemsPath, itemKeyPath, DiscoveredEndpoint.clip(firstText(get.path("description"), get.path("summary")),
                    DiscoveredEndpoint.MAX_DESCRIPTION), EndpointSource.DOCS));
        }
        return Optional.of(DiscoveredEndpoint.distinct(endpoints));
    }

    private JsonNode parse(String document) {
        if (document == null || document.isBlank()) return null;
        String trimmed = document.stripLeading();
        if (trimmed.startsWith("<")) return null;
        try {
            if (trimmed.startsWith("{")) return objectMapper.readTree(document);
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(MAX_ALIASES);
            DumperOptions dumper = new DumperOptions();
            Object loaded = new Yaml(new SafeConstructor(options), new Representer(dumper), dumper, options).load(document);
            return loaded instanceof Map<?, ?> ? objectMapper.valueToTree(loaded) : null;
        } catch (JacksonException | YAMLException | IllegalArgumentException e) {
            return null;
        }
    }

    /** The path of the first server URL; nothing for a templated or unreadable one. */
    private static String serverPath(JsonNode root) {
        String url = root.path("servers").path(0).path("url").asString("");
        if (url.isEmpty() || url.contains("{")) return "";
        try {
            String path = URI.create(url).getRawPath();
            return path == null ? "" : path;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** What lies between the base URL's path and the document's paths: {@code /api} + server {@code /api/v2} → {@code /v2}. */
    private static String prefix(String serverPath, BaseUrl base) {
        String basePath = URI.create(base.value()).getRawPath();
        basePath = basePath == null ? "" : basePath;
        String server = serverPath.endsWith("/") ? serverPath.substring(0, serverPath.length() - 1) : serverPath;
        if (server.isEmpty() || !server.startsWith(basePath)) return "";
        String rest = server.substring(basePath.length());
        return rest.isEmpty() || rest.startsWith("/") ? rest : "";
    }

    /** Required query parameters with a value from the docs; empty when one has none (the operation cannot be polled). */
    private Optional<Map<String, String>> query(JsonNode root, JsonNode pathParameters, JsonNode operationParameters) {
        Map<String, String> query = new LinkedHashMap<>();
        for (JsonNode parameters : List.of(pathParameters, operationParameters)) {
            for (int i = 0; i < parameters.size(); i++) {
                JsonNode parameter = resolve(root, parameters.get(i));
                if (!"query".equals(parameter.path("in").asString("")) || !parameter.path("required").asBoolean(false)) continue;
                JsonNode schema = resolve(root, parameter.path("schema"));
                String value = firstText(parameter.path("example"), schema.path("example"), schema.path("default"),
                        parameter.path("default"), schema.path("enum").path(0), parameter.path("enum").path(0));
                String name = parameter.path("name").asString("");
                if (value == null || name.isEmpty()) return Optional.empty();
                query.put(name, value);
            }
        }
        return Optional.of(query);
    }

    private static JsonNode responseSchema(JsonNode get, boolean openApi3) {
        JsonNode ok = get.path("responses").path("200");
        if (!openApi3) return ok.path("schema");
        JsonNode content = ok.path("content");
        if (content.has("application/json")) return content.path("application/json").path("schema");
        for (Map.Entry<String, JsonNode> type : content.properties()) {
            if (type.getKey().contains("json")) return type.getValue().path("schema");
        }
        return content.path("missing");
    }

    /** Follows local {@code $ref}s ({@code #/components/schemas/X}, {@code #/definitions/X}, parameters). */
    private static JsonNode resolve(JsonNode root, JsonNode node) {
        JsonNode current = node;
        for (int depth = 0; depth < MAX_REF_DEPTH && current.has("$ref"); depth++) {
            String ref = current.path("$ref").asString("");
            if (!ref.startsWith("#/")) return current.path("missing");
            current = root.at(ref.substring(1));
        }
        return current;
    }

    private static boolean isList(JsonNode schema) {
        return "array".equals(schema.path("type").asString("")) || (schema.has("items") && !schema.has("properties"));
    }

    private static String firstText(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate.isValueNode() && !candidate.isNull()) {
                String text = candidate.asString("").strip();
                if (!text.isEmpty()) return text;
            }
        }
        return null;
    }
}
