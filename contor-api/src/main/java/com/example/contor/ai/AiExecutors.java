package com.example.contor.ai;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background executors of the AI pipeline. {@code analysis} has a single thread: the GPU runs one model call at a time,
 * so analysis runs and new-mail processing queue up behind each other. {@code pull} downloads models independently.
 * Not exposed as {@link Executor} beans, so Spring Boot's own task executor stays auto-configured.
 */
public final class AiExecutors implements AutoCloseable {

    private final Executor analysis;
    private final Executor pull;

    public AiExecutors(Executor analysis, Executor pull) {
        this.analysis = analysis;
        this.pull = pull;
    }

    public static AiExecutors create() {
        return new AiExecutors(
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("ai-analysis").daemon(true).factory()),
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ai-pull-", 0).factory()));
    }

    public Executor analysis() {
        return analysis;
    }

    public Executor pull() {
        return pull;
    }

    @Override
    public void close() {
        for (Executor executor : new Executor[]{analysis, pull}) {
            if (executor instanceof ExecutorService service) {
                service.shutdownNow();
            }
        }
    }
}
