package com.example.contor.ai;

import java.time.Instant;

/**
 * One line of the mail digest the insight pass reads: who, what, when — no body beyond the preview.
 *
 * @param folder {@code sent} for the user's own mail, else the inbox
 */
public record DigestEmail(Long id, String fromName, String fromAddress, String subject, String snippet, Instant receivedAt,
                          String folder) {
}
