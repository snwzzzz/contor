package com.example.contor.ai.eval;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Reads eval cases: one JSON file per case; a missing {@code id} defaults to the file name. */
final class EvalCaseLoader {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private EvalCaseLoader() {
    }

    /** Cases from a test-resource folder such as {@code ai-eval/synthetic}. */
    static List<EvalCase> loadClasspath(String folder) {
        URL url = EvalCaseLoader.class.getClassLoader().getResource(folder);
        if (url == null) {
            throw new IllegalStateException("Eval folder not on the classpath: " + folder);
        }
        try {
            return load(Path.of(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cases from a directory (empty when it does not exist, e.g. no private corpus). */
    static List<EvalCase> load(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().map(EvalCaseLoader::read).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static EvalCase read(Path file) {
        try {
            EvalCase parsed = MAPPER.readValue(Files.readString(file), EvalCase.class);
            if (parsed.id() != null && !parsed.id().isBlank()) {
                return parsed;
            }
            String name = file.getFileName().toString();
            return new EvalCase(name.substring(0, name.length() - ".json".length()), parsed.email(), parsed.expected(), parsed.mustNotProduce(), parsed.needsReply(),
                    parsed.context());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read eval case " + file + ": " + e.getMessage(), e);
        }
    }
}
