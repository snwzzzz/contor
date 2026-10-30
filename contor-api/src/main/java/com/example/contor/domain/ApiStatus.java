package com.example.contor.domain;

import java.util.Set;

/**
 * Health of an external API. FAILING after repeated errors (still retried with backoff), PAUSED after many errors or by
 * the user, NEEDS_SETUP after 401/403 (no retries until the user fixes the credentials).
 */
public enum ApiStatus {
    ACTIVE, FAILING, PAUSED, NEEDS_SETUP;

    /** The scheduler calls apps in these states when they are due. */
    public static final Set<ApiStatus> POLLED = Set.of(ACTIVE, FAILING);
}
