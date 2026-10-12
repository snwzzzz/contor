package com.example.contor.service;

import com.example.contor.ai.MailContext;
import com.example.contor.ai.MailContextPrompt;
import com.example.contor.config.AiProperties;
import com.example.contor.config.MailContextProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.RecordSource;
import com.example.contor.persistence.EmailEmbeddingRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.NearEmail;
import com.example.contor.persistence.RecordSourceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collects what the user's other mail says about an email (ADR 0009): the conversation before it, earlier emails from the
 * same sender, emails close in meaning (embeddings) and the records already created from any of them. Only mail received
 * before the email counts (an older email must not be read with knowledge of later ones), and only the mail of the
 * email's owner. No model calls: the email's own vector is already stored.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MailContextService {

    private final EmailMessageRepository emailMessageRepository;
    private final EmailEmbeddingRepository emailEmbeddingRepository;
    private final RecordSourceRepository recordSourceRepository;
    private final RecordService recordService;
    private final AiProperties aiProperties;
    private final MailContextProperties properties;

    /** The context of an inbox email; empty for sent mail, unknown emails or when turned off. */
    @Transactional
    public MailContext contextFor(Long emailId) {
        if (!properties.enabled()) {
            return MailContext.EMPTY;
        }
        EmailMessage email = emailMessageRepository.findById(new EmailMessage.EmailMessageId(emailId))
                .filter(found -> !EmailMessage.FOLDER_SENT.equals(found.getFolder()))
                .orElse(null);
        if (email == null) {
            return MailContext.EMPTY;
        }
        AppUser.AppUserId owner = email.getAccount().getOwner().getId();
        Instant before = email.getReceivedAt();
        Set<Long> seen = new LinkedHashSet<>(List.of(emailId));

        List<EmailMessage> thread = email.getConversationId() == null ? List.of()
                : emailMessageRepository.findThread(email.getAccount().getId(), email.getConversationId()).stream()
                .filter(message -> !message.getId().equals(email.getId()) && message.getReceivedAt().isBefore(before))
                .toList();
        thread.forEach(message -> seen.add(message.getId().id()));

        List<EmailMessage> sender = email.getFromAddress() == null ? List.of()
                : emailMessageRepository.findFromSenderBefore(owner, email.getFromAddress(), before,
                        PageRequest.of(0, properties.senderEmails() + thread.size())).stream()
                .filter(message -> seen.add(message.getId().id()))
                .limit(properties.senderEmails())
                .toList();

        List<EmailMessage> related = related(email, owner, before, seen);

        List<EmailMessage> all = new ArrayList<>(thread);
        all.addAll(sender);
        all.addAll(related);
        return new MailContext(thread.stream().map(ThreadMessages::of).toList(), sender.stream().map(ThreadMessages::of).toList(),
                related.stream().map(ThreadMessages::of).toList(), records(all));
    }

    /**
     * {@link #contextFor} without the conversation, as prompt text: for the reply composer, which reads the whole
     * conversation itself.
     */
    @Transactional
    public String contextTextWithoutThread(Long emailId) {
        MailContext context = contextFor(emailId);
        return MailContextPrompt.render(new MailContext(List.of(), context.fromSender(), context.related(), context.records()),
                aiProperties.timezone(), properties.budgetChars());
    }

    /** {@link #contextFor} as prompt text within the configured budget; "" when there is none. */
    @Transactional
    public String contextTextFor(Long emailId) {
        return MailContextPrompt.render(contextFor(emailId), aiProperties.timezone(), properties.budgetChars());
    }

    /** Emails close in meaning that are not already part of the context, closest first. */
    private List<EmailMessage> related(EmailMessage email, AppUser.AppUserId owner, Instant before, Set<Long> seen) {
        List<NearEmail> nearest = emailEmbeddingRepository.findNearest(owner, email.getId().id(), aiProperties.models().embedding(),
                before.minus(properties.relatedWithin()), before, properties.relatedMaxDistance(), properties.relatedEmails() + seen.size());
        List<EmailMessage.EmailMessageId> ids = nearest.stream().map(NearEmail::emailId).filter(id -> !seen.contains(id))
                .limit(properties.relatedEmails()).map(EmailMessage.EmailMessageId::new).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, EmailMessage> byId = new LinkedHashMap<>();
        emailMessageRepository.findAllById(ids).forEach(message -> byId.put(message.getId().id(), message));
        return ids.stream().map(id -> byId.get(id.id())).filter(Objects::nonNull)
                .filter(message -> email.getConversationId() == null || !email.getConversationId().equals(message.getConversationId()))
                .toList();
    }

    /** "collection: title" of the records created from the emails; records that cannot be read are left out. */
    private List<String> records(List<EmailMessage> emails) {
        if (emails.isEmpty()) {
            return List.of();
        }
        Set<String> lines = new LinkedHashSet<>();
        for (RecordSource source : recordSourceRepository.findByEmail_IdIn(emails.stream().map(EmailMessage::getId).toList())) {
            try {
                String title = recordService.getRecord(source.getCollection().getId().id(), source.getRecordId()).getTitle();
                lines.add(source.getCollection().getSlug().value() + ": " + title);
            } catch (RuntimeException e) {
                log.debug("Record {} of collection {} left out of the context: {}", source.getRecordId(),
                        source.getCollection().getId().id(), e.getMessage());
            }
        }
        return List.copyOf(lines);
    }
}
