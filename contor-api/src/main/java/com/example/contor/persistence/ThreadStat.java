package com.example.contor.persistence;

import java.time.Instant;

/**
 * One conversation of a mailbox: how many messages it has (sent ones included) and when the user last replied.
 *
 * @param lastSentAt receivedAt of the newest sent message, {@code null} when the user never replied
 */
public record ThreadStat(Long accountId, String conversationId, Long messages, Instant lastSentAt) {
}
