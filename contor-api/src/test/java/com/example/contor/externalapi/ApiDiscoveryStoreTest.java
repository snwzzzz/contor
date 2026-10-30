package com.example.contor.externalapi;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.DiscoveryStatus;
import com.example.contor.domain.EndpointSource;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApiDiscoveryStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final AppUser.AppUserId ANNA = new AppUser.AppUserId(1L);
    private static final AppUser.AppUserId BEN = new AppUser.AppUserId(2L);

    /** A clock the test moves. */
    private static final class MovingClock extends Clock {
        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovingClock clock = new MovingClock();
    private final ApiDiscoveryStore store = new ApiDiscoveryStore(clock);

    @Test
    void a_discovery_is_found_by_its_owner_only() {
        var discovery = store.start(ANNA);

        assertThat(discovery.status()).isEqualTo(DiscoveryStatus.RUNNING);
        assertThat(discovery.startedAt()).isEqualTo(NOW);
        assertThat(store.find(discovery.id(), ANNA)).hasValue(discovery);
        assertThat(store.find(discovery.id(), BEN)).isEmpty();
        assertThat(store.find("unknown", ANNA)).isEmpty();
    }

    @Test
    void finishing_and_failing_replace_the_running_discovery() {
        var done = store.start(ANNA);
        var failed = store.start(ANNA);

        store.finish(done.id(), EndpointSource.DOCS, List.of());
        store.fail(failed.id(), "The documentation answered with HTTP 404");

        assertThat(store.find(done.id(), ANNA).orElseThrow().status()).isEqualTo(DiscoveryStatus.DONE);
        assertThat(store.find(failed.id(), ANNA).orElseThrow().error()).isEqualTo("The documentation answered with HTTP 404");
    }

    @Test
    void a_discovery_is_kept_for_an_hour() {
        var discovery = store.start(ANNA);

        clock.now = NOW.plus(Duration.ofMinutes(59));
        assertThat(store.find(discovery.id(), ANNA)).isPresent();
        clock.now = NOW.plus(Duration.ofMinutes(61));
        assertThat(store.find(discovery.id(), ANNA)).isEmpty();
    }

    @Test
    void keeps_the_newest_discoveries_of_each_user() {
        var first = store.start(ANNA);
        var others = store.start(BEN);
        for (int i = 0; i < ApiDiscoveryStore.MAX_PER_USER; i++) {
            clock.now = clock.now.plusSeconds(1);
            store.start(ANNA);
        }

        assertThat(store.find(first.id(), ANNA)).isEmpty();
        assertThat(store.find(others.id(), BEN)).isPresent();
    }
}
