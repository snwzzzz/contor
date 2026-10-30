package com.example.contor.externalapi;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.DiscoveryStatus;
import com.example.contor.domain.EndpointSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The users' "Read the docs" runs, in memory: a run is only needed while the modal is open, so it is kept for
 * {@link #KEPT} and at most {@link #MAX_PER_USER} per user (a restart forgets them; the user reads the docs again).
 */
@RequiredArgsConstructor
@Component
public class ApiDiscoveryStore {

    static final Duration KEPT = Duration.ofHours(1);
    static final int MAX_PER_USER = 10;

    private final Map<String, ApiDiscovery> discoveries = new ConcurrentHashMap<>();
    private final Clock clock;

    /** A new RUNNING discovery of {@code ownerId}. */
    public synchronized ApiDiscovery start(AppUser.AppUserId ownerId) {
        Instant now = clock.instant();
        discoveries.values().removeIf(discovery -> expired(discovery, now));
        List<ApiDiscovery> own = discoveries.values().stream().filter(discovery -> discovery.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(ApiDiscovery::startedAt)).toList();
        for (int i = 0; i <= own.size() - MAX_PER_USER; i++) {
            discoveries.remove(own.get(i).id());
        }
        ApiDiscovery discovery = new ApiDiscovery(UUID.randomUUID().toString(), ownerId, DiscoveryStatus.RUNNING, null, List.of(), null, now);
        discoveries.put(discovery.id(), discovery);
        return discovery;
    }

    public ApiDiscovery finish(String id, EndpointSource source, List<DiscoveredEndpoint> endpoints) {
        return discoveries.computeIfPresent(id, (key, discovery) -> discovery.done(source, endpoints));
    }

    public ApiDiscovery fail(String id, String error) {
        return discoveries.computeIfPresent(id, (key, discovery) -> discovery.failed(error));
    }

    /** The discovery when it belongs to {@code ownerId} and has not expired. */
    public Optional<ApiDiscovery> find(String id, AppUser.AppUserId ownerId) {
        return Optional.ofNullable(discoveries.get(id))
                .filter(discovery -> discovery.ownerId().equals(ownerId) && !expired(discovery, clock.instant()));
    }

    private static boolean expired(ApiDiscovery discovery, Instant now) {
        return !now.isBefore(discovery.startedAt().plus(KEPT));
    }
}
