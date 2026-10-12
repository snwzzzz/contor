package com.example.contor.ai;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Where the time of one email's AI processing goes: the worker opens a session per email ({@link #begin}), wraps each
 * stage ({@link #stage}), and {@link OllamaClient} adds every model call with the numbers Ollama reports. Sessions are
 * per thread (model calls are synchronous on the AI thread); outside a session stages just run. A stage inside a stage
 * counts towards the outer one. Holds numbers only, never prompts or answers.
 */
@Component
public class AiTimings {

    /** One model call as Ollama reports it: tokens read and written, model load, prompt reading and answer writing. */
    public record Call(String model, int promptTokens, int answerTokens, Duration load, Duration prompt, Duration answer) {
    }

    public record Stage(String name, Duration duration, List<Call> calls) {
    }

    public record Report(Long emailId, List<Stage> stages, Duration total) {

        /** One log line, e.g. {@code email 7: extract 1.23s [qwen 2140→310 tok, load 0.01s, …], total 1.23s}. */
        public String format() {
            String parts = stages.stream().map(Report::format).collect(Collectors.joining(", "));
            return "email " + emailId + ": " + (parts.isEmpty() ? "" : parts + ", ") + "total " + seconds(total);
        }

        private static String format(Stage stage) {
            String text = stage.name() + " " + seconds(stage.duration());
            if (stage.calls().isEmpty()) {
                return text;
            }
            return text + " [" + stage.calls().stream()
                    .map(call -> call.model() + " " + call.promptTokens() + "→" + call.answerTokens() + " tok, load "
                            + seconds(call.load()) + ", prompt " + seconds(call.prompt()) + ", answer " + seconds(call.answer()))
                    .collect(Collectors.joining("; ")) + "]";
        }

        private static String seconds(Duration duration) {
            return String.format(Locale.ROOT, "%.2fs", duration.toNanos() / 1e9);
        }
    }

    private static final class Session {
        private final Long emailId;
        private final long startedAt;
        private final List<Stage> stages = new ArrayList<>();
        private List<Call> currentCalls;

        private Session(Long emailId, long startedAt) {
            this.emailId = emailId;
            this.startedAt = startedAt;
        }
    }

    private final ThreadLocal<Session> current = new ThreadLocal<>();
    private final LongSupplier nanoTime;

    public AiTimings() {
        this(System::nanoTime);
    }

    AiTimings(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    /** Starts timing one email on this thread (replaces an unfinished session). */
    public void begin(Long emailId) {
        current.set(new Session(emailId, nanoTime.getAsLong()));
    }

    /** Runs {@code work} as the named stage; it is recorded even when it throws. */
    public <T> T stage(String name, Supplier<T> work) {
        Session session = current.get();
        if (session == null || session.currentCalls != null) {
            return work.get();
        }
        long start = nanoTime.getAsLong();
        List<Call> calls = new ArrayList<>();
        session.currentCalls = calls;
        try {
            return work.get();
        } finally {
            session.currentCalls = null;
            session.stages.add(new Stage(name, Duration.ofNanos(nanoTime.getAsLong() - start), List.copyOf(calls)));
        }
    }

    public void stage(String name, Runnable work) {
        stage(name, () -> {
            work.run();
            return null;
        });
    }

    /** Adds a model call to the open stage; ignored outside a stage. */
    public void recordCall(Call call) {
        Session session = current.get();
        if (session != null && session.currentCalls != null) {
            session.currentCalls.add(call);
        }
    }

    /** Ends this thread's session; empty when none was open. */
    public Optional<Report> finish() {
        Session session = current.get();
        current.remove();
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(new Report(session.emailId, List.copyOf(session.stages),
                Duration.ofNanos(nanoTime.getAsLong() - session.startedAt)));
    }
}
