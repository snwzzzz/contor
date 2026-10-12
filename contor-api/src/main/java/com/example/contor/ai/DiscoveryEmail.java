package com.example.contor.ai;

import java.time.Instant;

/** Header-level view of an email for category discovery (JPQL constructor projection; no bodies loaded). */
public record DiscoveryEmail(String fromName, String fromAddress, String subject, String snippet, Instant receivedAt) {
}
