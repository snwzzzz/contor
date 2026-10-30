package com.example.contor.ai.eval;

import com.example.contor.ai.CachingLlmClient;
import com.example.contor.ai.LlmClient;
import com.example.contor.ai.OllamaClient;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.ApiFieldMapping;
import com.example.contor.externalapi.JsonItems;
import com.example.contor.service.ApiMappingModelService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mapping proposal ({@link ApiMappingModelService}, {@code api-mapping-v1}) against the local Ollama: each case in
 * {@code ai-eval/api-mapping} is a few items of an endpoint with a collection catalog, the collection they belong in
 * ({@code none} when none fits) and fields a good mapping fills. Counts the right collections and expected fields found,
 * and prints every mapping. Report only. {@code ./mvnw verify -Pai-eval -Dtest=AiApiMappingEvalTest}.
 */
@Tag("ai-eval")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AiApiMappingEvalTest {

    private static final String DEFAULT_MODEL = "qwen3.5:9b";

    record CatalogEntry(String slug, String name, List<List<String>> fields) {
    }

    record MappingCase(String id, String api, String endpoint, String description, List<Map<String, Object>> samples,
                       List<CatalogEntry> catalog, String expectedCollection, Map<String, String> expectedFields) {
    }

    @Test
    void the_mapping_puts_the_items_into_the_right_collection_and_fields() {
        String model = System.getProperty("ai.eval.model", DEFAULT_MODEL);
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(5), 16384, "30m"),
                new AiProperties.Models(model, model, "nomic-embed-text"),
                new AiProperties.Processing(false, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
        JsonMapper mapper = JsonMapper.builder().build();
        CachingLlmClient ollama = CachingLlmClient.fromSystemProperties(new OllamaClient(properties, mapper), properties.ollama().numCtx());
        // -Dai.eval.raw=true prints the model's raw answer, to see what the validation dropped
        LlmClient client = (llmModel, system, user, schema) -> {
            String answer = ollama.completeJson(llmModel, system, user, schema);
            if (Boolean.getBoolean("ai.eval.raw")) {
                System.out.println("[ai-eval] raw answer: " + answer.replaceAll("\\s+", " "));
            }
            return answer;
        };
        ApiMappingModelService service = new ApiMappingModelService(client, properties, mapper, new JsonItems(mapper));

        int cases = 0;
        int rightCollection = 0;
        int expectedFields = 0;
        int foundFields = 0;
        for (MappingCase mappingCase : CaseFilter.fromSystemProperty().selectOrSkip(load(mapper), MappingCase::id)) {
            cases++;
            List<CollectionDto> catalog = catalog(mappingCase.catalog());
            Optional<ApiMappingModelService.MappingDraft> draft = service.propose(mappingCase.api(), mappingCase.endpoint(),
                    mappingCase.description(), mappingCase.samples(), catalog);
            String collection = draft.flatMap(found -> catalog.stream().filter(entry -> entry.getId().equals(found.collectionId())).findFirst())
                    .map(CollectionDto::getSlug).orElse("none");
            Map<String, String> fields = draft.map(found -> found.fields().stream()
                    .collect(Collectors.toMap(ApiFieldMapping::fieldSlug, ApiFieldMapping::path))).orElse(Map.of());
            System.out.printf("[ai-eval] %s: %s title %s fields %s (confidence %s) %s%n", mappingCase.id(), collection,
                    draft.map(found -> String.valueOf(found.titlePath())).orElse("-"), fields,
                    draft.map(found -> String.valueOf(found.confidence())).orElse("-"), draft.map(found -> found.reasoning()).orElse(""));
            boolean right = collection.equals(mappingCase.expectedCollection());
            if (right) rightCollection++;
            System.out.printf("[ai-eval] %s: collection %s %s%n", mappingCase.id(), mappingCase.expectedCollection(), right ? "right" : "WRONG");
            for (Map.Entry<String, String> expected : mappingCase.expectedFields().entrySet()) {
                expectedFields++;
                boolean hit = expected.getValue().equals(fields.get(expected.getKey()));
                if (hit) foundFields++;
                System.out.printf("[ai-eval] %s: %s <- %s %s%n", mappingCase.id(), expected.getKey(), expected.getValue(), hit ? "found" : "MISSED");
            }
        }
        System.out.printf("[ai-eval] api mapping (%s, %s): %d of %d collections right, %d of %d expected fields%n", model,
                ApiMappingModelService.PROMPT_VERSION, rightCollection, cases, foundFields, expectedFields);
        System.out.println("[ai-eval] " + ollama.summary());
        assertThat(cases).as("api mapping cases").isPositive();
    }

    private static List<CollectionDto> catalog(List<CatalogEntry> entries) {
        long id = 1;
        List<CollectionDto> catalog = new ArrayList<>();
        for (CatalogEntry entry : entries) {
            CollectionDto dto = new CollectionDto().id(id++).slug(entry.slug()).name(entry.name());
            int position = 0;
            for (List<String> field : entry.fields()) {
                dto.addFieldsItem(new CollectionFieldDto((long) position, field.get(0), field.get(0), CollectionFieldType.valueOf(field.get(1)),
                        new LinkedHashMap<>(), false, false, position++));
            }
            catalog.add(dto);
        }
        return catalog;
    }

    private static List<MappingCase> load(JsonMapper mapper) {
        URL url = AiApiMappingEvalTest.class.getClassLoader().getResource("ai-eval/api-mapping");
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted()
                    .map(file -> {
                        try {
                            return mapper.readValue(Files.readString(file), MappingCase.class);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
