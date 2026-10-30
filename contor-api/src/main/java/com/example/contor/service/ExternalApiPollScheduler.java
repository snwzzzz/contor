package com.example.contor.service;

import com.example.contor.config.ExternalApiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Calls the due apps every poll tick ({@code app.external-apis.poll-tick}, off with {@code app.external-apis.enabled=false}):
 * on virtual threads, at most {@code max-concurrent} at once and one app per host at a time, each in its own
 * {@link ExternalApiPoller#poll} transaction. The tick waits for all of them (fixed delay: ticks never overlap) and
 * prunes the call history once an hour. New and changed items then go to {@link ExternalApiIngestService#afterPoll}.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ExternalApiPollScheduler {

    private static final Duration PRUNE_EVERY = Duration.ofHours(1);

    private final ExternalApiPoller poller;
    private final ExternalApiIngestService ingestService;
    private final ExternalApiProperties properties;
    private final Clock clock;
    private Instant lastPruned;

    @Scheduled(fixedDelayString = "${app.external-apis.poll-tick:1m}", initialDelayString = "${app.external-apis.poll-tick:1m}")
    public void scheduledPoll() {
        if (!properties.enabled()) return;
        Instant now = clock.instant();
        if (lastPruned == null || !now.isBefore(lastPruned.plus(PRUNE_EVERY))) {
            lastPruned = now;
            try {
                int removed = poller.pruneHistory();
                if (removed > 0) log.info("Removed {} old external API calls", removed);
            } catch (RuntimeException e) {
                log.warn("Pruning the external API call history failed: {}", e.getMessage());
            }
        }
        pollDue();
    }

    /** Polls every due app now and returns when all are done. */
    public void pollDue() {
        Map<String, List<Long>> byHost = poller.findDue().stream().collect(Collectors.groupingBy(ExternalApiPoller.DueApi::host,
                LinkedHashMap::new, Collectors.mapping(ExternalApiPoller.DueApi::id, Collectors.toList())));
        Semaphore slots = new Semaphore(Math.max(1, properties.maxConcurrent()));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            byHost.values().forEach(ids -> executor.submit(() -> pollInTurn(ids, slots)));
        }
    }

    private void pollInTurn(List<Long> ids, Semaphore slots) {
        for (Long id : ids) {
            try {
                slots.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            List<ExternalApiPoller.ItemChanges> changes;
            try {
                changes = poller.poll(id);
            } catch (RuntimeException e) {
                // Logged by id only: messages of other layers may name hosts, never credentials, but stay out of here.
                log.error("Polling external API {} failed", id, e);
                continue;
            } finally {
                slots.release();
            }
            // After the poll's transaction: the ingest submits each proposal in its own.
            for (ExternalApiPoller.ItemChanges change : changes != null ? changes : List.<ExternalApiPoller.ItemChanges>of()) {
                try {
                    ingestService.afterPoll(change.endpointId(), change.itemIds());
                } catch (RuntimeException e) {
                    log.error("Ingesting the items of external API endpoint {} failed", change.endpointId(), e);
                }
            }
        }
    }
}
