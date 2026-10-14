package com.example.contor.ai;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Record and replay of model answers: an answer is stored under the hash of everything that decides it (model, context
 * size, system prompt, user prompt, JSON schema), so a later run asks the model only for prompts that changed. Used by
 * the AI evaluation ({@code -Dai.eval.cache}) and, in the {@code dev} profile only, by the app ({@code AI_CACHE},
 * {@code DevAiCacheConfig}); the stored answers are plain text about your mail, so never outside development.
 * <p>
 * Bump {@link #KEY_VERSION} when {@link OllamaClient} sends other options (temperature, thinking), as those are not part
 * of the {@link LlmClient} call. Thread-safe: the app calls it from several AI threads.
 */
public final class CachingLlmClient implements LlmClient {

    /** Default folder (relative to {@code contor-api}): the repo-root {@code .ai-cache/}, gitignored. */
    public static final String DEFAULT_DIRECTORY = "../.ai-cache";

    static final String KEY_VERSION = "1";

    public enum Mode {
        /** Answer from the cache, ask the model only on a miss and store its answer. */
        ON,
        /** Always ask the model, never touch the cache. */
        OFF,
        /** Always ask the model and overwrite the stored answer. */
        REFRESH;

        public static Mode parse(String value) {
            if (value == null || value.isBlank()) {
                return OFF;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("The AI cache mode must be on, off or refresh, not " + value, e);
            }
        }
    }

    private final LlmClient delegate;
    private final Path directory;
    private final Mode mode;
    private final int numCtx;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger misses = new AtomicInteger();

    public CachingLlmClient(LlmClient delegate, Path directory, Mode mode, int numCtx) {
        this.delegate = delegate;
        this.directory = directory;
        this.mode = mode;
        this.numCtx = numCtx;
    }

    /** Wraps {@code delegate} as the {@code ai.eval.cache} and {@code ai.eval.cacheDir} system properties say. */
    public static CachingLlmClient fromSystemProperties(LlmClient delegate, int numCtx) {
        return new CachingLlmClient(delegate, Path.of(System.getProperty("ai.eval.cacheDir", DEFAULT_DIRECTORY)),
                Mode.parse(System.getProperty("ai.eval.cache")), numCtx);
    }

    @Override
    public String completeJson(String model, String systemPrompt, String userPrompt, JsonNode jsonSchema) {
        Path file = directory.resolve(key(model, systemPrompt, userPrompt, jsonSchema) + ".json");
        if (mode == Mode.ON && Files.isRegularFile(file)) {
            hits.incrementAndGet();
            return read(file);
        }
        misses.incrementAndGet();
        String answer = delegate.completeJson(model, systemPrompt, userPrompt, jsonSchema);
        if (mode != Mode.OFF) {
            write(file, answer);
        }
        return answer;
    }

    public Mode mode() {
        return mode;
    }

    public Path directory() {
        return directory;
    }

    public int hits() {
        return hits.get();
    }

    public int misses() {
        return misses.get();
    }

    public String summary() {
        return "cache %s: %d hits, %d model calls".formatted(mode.name().toLowerCase(Locale.ROOT), hits.get(), misses.get());
    }

    /** Length-prefixed parts, so text moving from the system to the user prompt never yields the same key. */
    private String key(String model, String systemPrompt, String userPrompt, JsonNode jsonSchema) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : new String[] {KEY_VERSION, String.valueOf(numCtx), model, systemPrompt, userPrompt, String.valueOf(jsonSchema)}) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Through a temporary file, so an aborted run never leaves half an answer behind. */
    private void write(Path file, String answer) {
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "answer", ".tmp");
            Files.writeString(temporary, answer);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
