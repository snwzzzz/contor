package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Security settings ({@code app.security.*}). Session idle timeout and cookie flags are configured through
 * {@code spring.session.timeout} and {@code server.servlet.session.cookie.*} (see application.yml).
 *
 * @param secureCookies          send session and CSRF cookies with {@code Secure} (enable when served over HTTPS)
 * @param sessionAbsoluteTimeout maximum session lifetime regardless of activity
 * @param maxSessionsPerUser     concurrent sessions per user; logging in beyond that expires the oldest session
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @DefaultValue("false") boolean secureCookies,
        @DefaultValue("12h") Duration sessionAbsoluteTimeout,
        @DefaultValue("3") int maxSessionsPerUser,
        @DefaultValue Lockout lockout,
        @DefaultValue LoginRateLimit loginRateLimit) {

    /** Per-username lockout: after {@code maxFailedAttempts} failures, lock for {@code baseDuration * 2^(lockouts-1)}, capped. */
    public record Lockout(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15m") Duration baseDuration,
            @DefaultValue("24h") Duration maxDuration) {
    }

    /** Per-IP limit on login requests within a sliding window. */
    public record LoginRateLimit(
            @DefaultValue("10") int maxAttempts,
            @DefaultValue("1m") Duration window) {
    }
}
