package com.example.contor.ai;

import java.time.Instant;
import java.util.List;

/**
 * The parts of an email the model sees, detached from JPA so the (slow) model call runs outside any transaction.
 *
 * @param mailbox address of the mailbox the email arrived in (the user), so the model does not file the user as a contact
 * @param body    plain text (HTML already stripped)
 */
public record EmailContent(Long id, String mailbox, String fromName, String fromAddress, List<String> to, String subject,
                           Instant receivedAt, String body) {
}
