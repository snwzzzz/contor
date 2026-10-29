package com.example.contor.service;

import com.example.contor.config.ExternalApiProperties;
import com.example.contor.domain.ApiStatus;
import com.example.contor.domain.CallOutcome;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiCall;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.ExternalApiSnapshot;
import com.example.contor.externalapi.ApiAuthException;
import com.example.contor.externalapi.ApiResponse;
import com.example.contor.externalapi.ExternalApiClient;
import com.example.contor.externalapi.JsonItems;
import com.example.contor.persistence.ExternalApiCallRepository;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiItemRepository;
import com.example.contor.persistence.ExternalApiRepository;
import com.example.contor.persistence.ExternalApiSnapshotRepository;
import com.example.contor.security.OutboundRequestException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One poll of one app (ADR 0012): calls its enabled endpoints, records each call, keeps the last answers and diffs the
 * items by key (new, changed, gone). Then it schedules the next call: the interval ± 10 % on success, an exponential
 * backoff on failure (FAILING after 3, PAUSED after 10), the server's {@code Retry-After} on 429, and no further calls
 * after rejected or missing credentials (NEEDS_SETUP). The daily request limit counts the calls of the last 24 hours.
 * Called by {@link ExternalApiPollScheduler}, one transaction per app.
 */
@RequiredArgsConstructor
@Service
public class ExternalApiPoller {

    static final int FAILING_AFTER = 3;
    static final int PAUSED_AFTER = 10;
    static final Duration MAX_BACKOFF = Duration.ofHours(24);
    private static final int MAX_ERROR = 500;

    private final ExternalApiRepository externalApiRepository;
    private final ExternalApiEndpointRepository externalApiEndpointRepository;
    private final ExternalApiCallRepository externalApiCallRepository;
    private final ExternalApiSnapshotRepository externalApiSnapshotRepository;
    private final ExternalApiItemRepository externalApiItemRepository;
    private final ExternalApiClient externalApiClient;
    private final JsonItems jsonItems;
    private final ExternalApiProperties properties;
    private final Clock clock;

    /** An app whose call is due, with the host it calls (one call per host at a time). */
    public record DueApi(long id, String host) {
    }

    /** What one endpoint's call means for the app, mildest first: the worst of a run decides. */
    private enum Effect { OK, RATE_LIMITED, FAILURE, NEEDS_SETUP }

    private record Result(Effect effect, String error, Duration retryAfter) {
        static Result ok() {
            return new Result(Effect.OK, null, null);
        }
    }

    @Transactional
    public List<DueApi> findDue() {
        return externalApiRepository.findByEnabledTrueAndStatusInAndNextCallAtLessThanEqual(ApiStatus.POLLED, clock.instant()).stream()
                .map(api -> new DueApi(api.getId().id(), URI.create(api.getBaseUrl().value()).getHost()))
                .toList();
    }

    @Transactional
    public void poll(Long apiId) {
        Optional<ExternalApi> found = externalApiRepository.findById(new ExternalApi.ExternalApiId(apiId));
        if (found.isEmpty()) return;
        ExternalApi api = found.get();
        Instant now = clock.instant();
        // Changed since it was picked (paused, turned off, credentials rejected by a parallel run): leave it.
        if (!api.isEnabled() || !ApiStatus.POLLED.contains(api.getStatus())) return;

        List<ExternalApiEndpoint> endpoints = externalApiEndpointRepository.findByApi_IdAndEnabledTrue(api.getId());
        long used = externalApiCallRepository.countByEndpoint_Api_IdAndStartedAtGreaterThanEqual(api.getId(), now.minus(Duration.ofDays(1)));
        boolean called = false;
        Result outcome = Result.ok();
        for (ExternalApiEndpoint endpoint : endpoints) {
            if (used >= api.getDailyRequestLimit()) break;
            used++;
            called = true;
            Result result = call(api, endpoint, now);
            if (result.effect().compareTo(outcome.effect()) > 0) outcome = result;
            if (result.effect() == Effect.NEEDS_SETUP) break;
        }
        if (called) api.setLastCalledAt(now);
        schedule(api, outcome, now);
        externalApiRepository.saveAndFlush(api);
    }

    /** Removes calls older than the history; returns how many. */
    @Transactional
    public int pruneHistory() {
        return externalApiCallRepository.deleteStartedBefore(clock.instant().minus(Duration.ofDays(properties.historyDays())));
    }

    private Result call(ExternalApi api, ExternalApiEndpoint endpoint, Instant now) {
        ExternalApiCall call = ExternalApiCall.builder().endpoint(endpoint).startedAt(now).build();
        long start = System.nanoTime();
        Result result;
        try {
            ApiResponse response = externalApiClient.call(api, endpoint);
            call.setHttpStatus(response.status());
            result = answer(endpoint, response, call, now);
        } catch (ApiAuthException e) {
            result = new Result(e.credentialsRejected() ? Effect.NEEDS_SETUP : Effect.FAILURE, e.getMessage(), null);
        } catch (OutboundRequestException e) {
            result = new Result(Effect.FAILURE, e.getMessage(), null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Missing settings or unreadable credentials: nothing was sent; the user has to fix the app.
            result = new Result(Effect.NEEDS_SETUP, e.getMessage(), null);
        }
        call.setDurationMs((int) Duration.ofNanos(System.nanoTime() - start).toMillis());
        if (result.effect() != Effect.OK) call.setOutcome(CallOutcome.FAILED);
        if (result.error() != null) call.setError(truncate(result.error()));
        externalApiCallRepository.saveAndFlush(call);
        return result;
    }

    private Result answer(ExternalApiEndpoint endpoint, ApiResponse response, ExternalApiCall call, Instant now) {
        int status = response.status();
        if (status == 401 || status == 403) return new Result(Effect.NEEDS_SETUP, "The app refused the credentials (HTTP " + status + ")", null);
        if (status == 429) return new Result(Effect.RATE_LIMITED, "Too many requests (HTTP 429)", response.retryAfter());
        if (!response.isSuccess()) return new Result(Effect.FAILURE, "The app answered with HTTP " + status, null);

        String hash = JsonItems.hash(response.body());
        boolean unchanged = externalApiSnapshotRepository.findFirstByEndpoint_IdOrderByFetchedAtDesc(endpoint.getId())
                .map(latest -> hash.equals(latest.getContentHash()))
                .orElse(false);
        if (unchanged) {
            call.setOutcome(CallOutcome.UNCHANGED);
            return Result.ok();
        }
        call.setOutcome(CallOutcome.OK);
        externalApiSnapshotRepository.saveAndFlush(ExternalApiSnapshot.builder().endpoint(endpoint).fetchedAt(now).contentHash(hash)
                .contentType(response.contentType()).body(response.body()).truncated(response.truncated()).build());
        pruneSnapshots(endpoint);

        if (response.truncated()) {
            call.setError("The answer is larger than " + size() + ", so no items were read");
            return Result.ok();
        }
        Optional<List<JsonItems.Item>> items = jsonItems.extract(response.body(), endpoint.getItemsPath(), endpoint.getItemKeyPath());
        if (items.isEmpty()) {
            call.setError("The answer is no JSON, so no items were read");
            return Result.ok();
        }
        diff(endpoint, items.get(), call, now);
        return Result.ok();
    }

    private void diff(ExternalApiEndpoint endpoint, List<JsonItems.Item> items, ExternalApiCall call, Instant now) {
        Map<String, ExternalApiItem> present = externalApiItemRepository.findByEndpoint_IdAndGoneAtIsNull(endpoint.getId()).stream()
                .collect(Collectors.toMap(ExternalApiItem::getItemKey, Function.identity(), (first, second) -> first));
        List<ExternalApiItem> changed = new ArrayList<>();
        for (JsonItems.Item item : items) {
            ExternalApiItem stored = present.remove(item.key());
            if (stored == null) {
                stored = externalApiItemRepository.findByEndpoint_IdAndItemKey(endpoint.getId(), item.key()).orElse(null);
                if (stored != null) {
                    // It disappeared earlier and is back.
                    stored.setGoneAt(null);
                    stored.setItemHash(item.hash());
                    stored.setData(new HashMap<>(item.data()));
                } else {
                    stored = ExternalApiItem.builder().endpoint(endpoint).itemKey(item.key()).itemHash(item.hash())
                            .data(new HashMap<>(item.data())).firstSeenAt(now).build();
                }
                call.setItemsNew(call.getItemsNew() + 1);
            } else if (!stored.getItemHash().equals(item.hash())) {
                stored.setItemHash(item.hash());
                stored.setData(new HashMap<>(item.data()));
                call.setItemsChanged(call.getItemsChanged() + 1);
            }
            stored.setLastSeenAt(now);
            changed.add(stored);
        }
        for (ExternalApiItem gone : present.values()) {
            gone.setGoneAt(now);
            changed.add(gone);
        }
        call.setItemsGone(present.size());
        if (!changed.isEmpty()) externalApiItemRepository.saveAllAndFlush(changed);
    }

    private void pruneSnapshots(ExternalApiEndpoint endpoint) {
        List<ExternalApiSnapshot> snapshots = externalApiSnapshotRepository.findByEndpoint_IdOrderByFetchedAtDesc(endpoint.getId());
        if (snapshots.size() > properties.snapshotsKept()) {
            externalApiSnapshotRepository.deleteAll(snapshots.subList(properties.snapshotsKept(), snapshots.size()));
        }
    }

    private void schedule(ExternalApi api, Result outcome, Instant now) {
        Duration interval = Duration.ofMinutes(api.getPollIntervalMinutes());
        switch (outcome.effect()) {
            case OK -> {
                api.setStatus(ApiStatus.ACTIVE);
                api.setFailureCount(0);
                api.setLastError(null);
                api.setNextCallAt(now.plus(jittered(interval, api, now)));
            }
            case RATE_LIMITED -> {
                api.setLastError(outcome.error());
                api.setNextCallAt(now.plus(outcome.retryAfter() != null ? outcome.retryAfter() : interval));
            }
            case NEEDS_SETUP -> {
                api.setStatus(ApiStatus.NEEDS_SETUP);
                api.setLastError(truncate(outcome.error()));
                api.setNextCallAt(null);
            }
            case FAILURE -> {
                int failures = api.getFailureCount() + 1;
                api.setFailureCount(failures);
                api.setLastError(truncate(outcome.error()));
                if (failures >= PAUSED_AFTER) {
                    api.setStatus(ApiStatus.PAUSED);
                    api.setNextCallAt(null);
                } else {
                    api.setStatus(failures >= FAILING_AFTER ? ApiStatus.FAILING : api.getStatus());
                    api.setNextCallAt(now.plus(backoff(interval, failures)));
                }
            }
        }
    }

    static Duration backoff(Duration interval, int failures) {
        Duration backoff = interval.multipliedBy(1L << Math.min(failures, 20));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    /** The interval ± 10 %, spread by app and time so apps added together do not stay in step. */
    private static Duration jittered(Duration interval, ExternalApi api, Instant now) {
        long spread = Math.floorMod(api.getId().id() * 31 + now.getEpochSecond(), 21) - 10;
        return interval.plus(interval.multipliedBy(spread).dividedBy(100));
    }

    private String size() {
        long bytes = properties.maxResponseSize().toBytes();
        return bytes % (1024 * 1024) == 0 ? bytes / (1024 * 1024) + " MB" : bytes / 1024 + " KB";
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_ERROR ? text : text.substring(0, MAX_ERROR);
    }
}
