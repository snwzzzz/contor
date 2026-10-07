package com.example.contor.mapper;

import com.example.contor.api.model.EmailDto;
import com.example.contor.api.model.EmailLabelDto;
import com.example.contor.api.model.EmailProcessingState;
import com.example.contor.api.model.EmailSummaryDto;
import com.example.contor.api.model.LabelOrigin;
import com.example.contor.api.model.MailAccountDto;
import com.example.contor.api.model.MailAccountState;
import com.example.contor.api.model.MailProviderKind;
import com.example.contor.api.model.RecipientDto;
import com.example.contor.domain.EmailLabel;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.Label;
import com.example.contor.domain.MailAccount;

import java.util.ArrayList;
import java.util.List;

/** Mailboxes and emails. Tokens are never mapped. */
public class MailMapper {

    public static MailAccountDto toDto(MailAccount account) {
        if (account == null) return null;

        return new MailAccountDto()
                .id(account.getId() != null ? account.getId().id() : null)
                .provider(account.getProvider() != null ? MailProviderKind.valueOf(account.getProvider().name()) : null)
                .emailAddress(account.getEmailAddress() != null ? account.getEmailAddress().value() : null)
                .displayName(account.getDisplayName())
                .status(account.getStatus() != null ? MailAccountState.valueOf(account.getStatus().name()) : null)
                .lastSyncedAt(MapperSupport.toOffset(account.getLastSyncedAt()));
    }

    public static EmailLabelDto toDto(EmailLabel emailLabel, Label label) {
        if (emailLabel == null || label == null) return null;

        return new EmailLabelDto()
                .labelId(label.getId() != null ? label.getId().id() : null)
                .slug(label.getSlug() != null ? label.getSlug().value() : null)
                .name(label.getName())
                .color(label.getColor() != null ? label.getColor().value() : null)
                .source(emailLabel.getSource() != null ? LabelOrigin.valueOf(emailLabel.getSource().name()) : null)
                .confidence(MapperSupport.normalize(emailLabel.getConfidence()));
    }

    public static EmailSummaryDto toSummaryDto(EmailMessage email, List<EmailLabelDto> labels) {
        if (email == null) return null;

        return new EmailSummaryDto()
                .id(email.getId() != null ? email.getId().id() : null)
                .accountId(accountId(email))
                .conversationId(email.getConversationId())
                .folder(email.getFolder())
                .fromAddress(email.getFromAddress())
                .fromName(email.getFromName())
                .subject(email.getSubject())
                .snippet(email.getSnippet())
                .receivedAt(MapperSupport.toOffset(email.getReceivedAt()))
                .read(email.isRead())
                .hasAttachments(email.isHasAttachments())
                .processingState(email.getProcessingState() != null ? EmailProcessingState.valueOf(email.getProcessingState().name()) : null)
                .labels(new ArrayList<>(labels));
    }

    public static EmailDto toDto(EmailMessage email, List<EmailLabelDto> labels) {
        if (email == null) return null;

        return new EmailDto()
                .id(email.getId() != null ? email.getId().id() : null)
                .accountId(accountId(email))
                .internetMessageId(email.getInternetMessageId())
                .conversationId(email.getConversationId())
                .folder(email.getFolder())
                .fromAddress(email.getFromAddress())
                .fromName(email.getFromName())
                .toRecipients(recipients(email.getToRecipients()))
                .ccRecipients(recipients(email.getCcRecipients()))
                .subject(email.getSubject())
                .snippet(email.getSnippet())
                .bodyText(email.getBodyText())
                .bodyHtml(email.getBodyHtml())
                .receivedAt(MapperSupport.toOffset(email.getReceivedAt()))
                .read(email.isRead())
                .hasAttachments(email.isHasAttachments())
                .processingState(email.getProcessingState() != null ? EmailProcessingState.valueOf(email.getProcessingState().name()) : null)
                .processingError(email.getProcessingError())
                .labels(new ArrayList<>(labels));
    }

    private static Long accountId(EmailMessage email) {
        return email.getAccount() != null && email.getAccount().getId() != null ? email.getAccount().getId().id() : null;
    }

    private static List<RecipientDto> recipients(List<EmailRecipient> recipients) {
        if (recipients == null) return new ArrayList<>();
        return new ArrayList<>(recipients.stream().map(r -> new RecipientDto().name(r.name()).address(r.address())).toList());
    }
}
