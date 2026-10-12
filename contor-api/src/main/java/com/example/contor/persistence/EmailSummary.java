package com.example.contor.persistence;

import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ProcessingState;

import java.time.Instant;
import java.util.List;

/** What an email list row shows: an email without its bodies (see {@link EmailSummaryQueries#findSummaries}). */
public record EmailSummary(Long id, Long accountId, String conversationId, String folder, String fromAddress, String fromName,
                           List<EmailRecipient> toRecipients, String subject, String snippet, Instant receivedAt, boolean read,
                           boolean hasAttachments, ProcessingState processingState) {

    /** The summary of a loaded email; {@code null} in → {@code null} out. */
    public static EmailSummary of(EmailMessage email) {
        if (email == null) return null;

        return new EmailSummary(
                email.getId() != null ? email.getId().id() : null,
                email.getAccount() != null && email.getAccount().getId() != null ? email.getAccount().getId().id() : null,
                email.getConversationId(), email.getFolder(), email.getFromAddress(), email.getFromName(), email.getToRecipients(),
                email.getSubject(), email.getSnippet(), email.getReceivedAt(), email.isRead(), email.isHasAttachments(),
                email.getProcessingState());
    }
}
