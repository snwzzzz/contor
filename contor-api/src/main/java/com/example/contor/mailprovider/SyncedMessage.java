package com.example.contor.mailprovider;

import com.example.contor.domain.EmailRecipient;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * A message as read from a provider. {@code null} = the provider sent no value and the sync keeps what is stored
 * (recipients excepted: no list means none). Only one body is set, the HTML one when the provider has it.
 */
@Builder
public record SyncedMessage(
        String providerMessageId,
        String internetMessageId,
        String conversationId,
        EmailRecipient from,
        List<EmailRecipient> to,
        List<EmailRecipient> cc,
        String subject,
        String snippet,
        String bodyText,
        String bodyHtml,
        Instant receivedAt,
        Boolean read,
        Boolean hasAttachments) {
}
