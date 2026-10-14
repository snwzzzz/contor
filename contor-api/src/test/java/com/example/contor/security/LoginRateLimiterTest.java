package com.example.contor.security;

import com.example.contor.MutableClock;
import com.example.contor.config.SecurityProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final LoginRateLimiter limiter = new LoginRateLimiter(new SecurityProperties.LoginRateLimit(3, Duration.ofMinutes(1)), clock);

    @Test
    void allows_attempts_up_to_the_limit_and_blocks_the_next() {
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();

        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    void counts_each_ip_separately() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("10.0.0.1");

        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
    }

    @Test
    void allows_again_once_the_window_has_passed() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("10.0.0.1");

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }
}
