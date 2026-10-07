package com.example.contor.security;

import com.example.contor.config.SecurityProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sliding-window limiter for login requests per client IP. Single instance app, so no shared store needed. */
public class LoginRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final SecurityProperties.LoginRateLimit limit;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(SecurityProperties.LoginRateLimit limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** Records an attempt and returns whether it is within the limit. */
    public boolean tryAcquire(String clientIp) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(limit.window());
        if (attempts.size() > CLEANUP_THRESHOLD) {
            attempts.values().removeIf(deque -> {
                synchronized (deque) {
                    return deque.isEmpty() || !deque.peekLast().isAfter(windowStart);
                }
            });
        }
        Deque<Instant> deque = attempts.computeIfAbsent(clientIp, ip -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && !deque.peekFirst().isAfter(windowStart)) {
                deque.pollFirst();
            }
            if (deque.size() >= limit.maxAttempts()) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
