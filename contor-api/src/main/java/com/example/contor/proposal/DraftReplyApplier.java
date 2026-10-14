package com.example.contor.proposal;

import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ReplyDraft;
import com.example.contor.domain.ReplyDraftStatus;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ReplyDraftRepository;
import com.example.contor.security.CurrentActor;
import com.example.contor.service.ReplyComposerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DRAFT_REPLY: {@code {"emailId", "to": [{"name", "address"}], "cc"?, "subject"?, "body", …}}. Keeps the (edited) text as a
 * READY {@link ReplyDraft} of the answered email; nothing is sent or written to the mailbox, so any user may approve it
 * for their own mail. The email must belong to the deciding user. Never logs the text.
 */
@RequiredArgsConstructor
@Component
public class DraftReplyApplier implements ProposalApplier {

    private final EmailMessageRepository emailMessageRepository;
    private final ReplyDraftRepository replyDraftRepository;

    @Override
    public ProposalKind kind() {
        return ProposalKind.DRAFT_REPLY;
    }

    @Override
    public Map<String, Object> apply(Proposal proposal) {
        Map<String, Object> payload = proposal.getFinalPayload();
        Long emailId = ProposalPayload.requiredId(payload, "emailId");
        List<EmailRecipient> to = recipients(payload, "to");
        if (to.isEmpty()) {
            throw new IllegalArgumentException("payload.to needs at least one recipient");
        }
        List<EmailRecipient> cc = recipients(payload, "cc");
        String body = ProposalPayload.optionalString(payload, "body");
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("payload.body is required");
        }
        String subject = ProposalPayload.optionalString(payload, "subject");

        Long userId = CurrentActor.userId().orElse(null);
        EmailMessage email = emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId))
                .filter(candidate -> userId != null && candidate.getAccount().getOwner().getId().id().equals(userId))
                .orElseThrow(() -> new EmailNotFoundException(emailId));

        ReplyDraft draft = replyDraftRepository.saveAndFlush(ReplyDraft.builder()
                .email(email)
                .account(email.getAccount())
                .conversationId(email.getConversationId())
                .toRecipients(to)
                .ccRecipients(cc)
                .subject(subject != null && !subject.isBlank() ? subject.strip() : ReplyComposerService.replySubject(email.getSubject()))
                .body(body.strip())
                .status(ReplyDraftStatus.READY)
                .proposal(proposal)
                .build());
        return Map.of("replyDraftId", draft.getId().id());
    }

    /** A list of {@code {"name"?, "address"}} objects; absent = empty. */
    private static List<EmailRecipient> recipients(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("payload." + key + " must be a list of recipients");
        }
        List<EmailRecipient> recipients = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> recipient) || !(recipient.get("address") instanceof String address) || address.isBlank()
                    || (recipient.get("name") != null && !(recipient.get("name") instanceof String))) {
                throw new IllegalArgumentException("payload." + key + " entries need an address");
            }
            recipients.add(new EmailRecipient((String) recipient.get("name"), address.strip()));
        }
        return recipients;
    }
}
