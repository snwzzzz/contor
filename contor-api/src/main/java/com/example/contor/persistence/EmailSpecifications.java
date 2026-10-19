package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailLabel;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Label;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.SearchField;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Email search: always limited to one owner's mailboxes, optional account, label, state and text filters. */
public final class EmailSpecifications {

    private static final char ESCAPE = '\\';

    private EmailSpecifications() {
    }

    /**
     * {@code null} filters are ignored; {@code ownerId} is mandatory; {@code text} is searched in {@code field}
     * (default ALL); {@code answered} true/false keeps only emails with/without a newer sent message in their conversation
     * (ignored for sent mail); {@code sent} lists the sent mail (Sent Items) instead of the inbox; {@code read}
     * true/false keeps only read/unread emails.
     */
    public record Criteria(AppUser.AppUserId ownerId, MailAccount.MailAccountId accountId, Label.LabelId labelId,
                           ProcessingState processingState, String text, SearchField field, Boolean answered, boolean sent,
                           Boolean read) {

        public Criteria(AppUser.AppUserId ownerId, MailAccount.MailAccountId accountId, Label.LabelId labelId,
                        ProcessingState processingState, String text, SearchField field, Boolean answered, boolean sent) {
            this(ownerId, accountId, labelId, processingState, text, field, answered, sent, null);
        }

        public Criteria(AppUser.AppUserId ownerId, MailAccount.MailAccountId accountId, Label.LabelId labelId,
                        ProcessingState processingState, String text, SearchField field, Boolean answered) {
            this(ownerId, accountId, labelId, processingState, text, field, answered, false);
        }

        public Criteria(AppUser.AppUserId ownerId, MailAccount.MailAccountId accountId, Label.LabelId labelId,
                        ProcessingState processingState, String text, SearchField field) {
            this(ownerId, accountId, labelId, processingState, text, field, null, false);
        }

        public Criteria(AppUser.AppUserId ownerId, MailAccount.MailAccountId accountId, Label.LabelId labelId,
                        ProcessingState processingState, String text) {
            this(ownerId, accountId, labelId, processingState, text, SearchField.ALL, null, false);
        }
    }

    public static Specification<EmailMessage> matching(Criteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<EmailMessage, MailAccount> account = root.join("account");
            predicates.add(cb.equal(account.get("owner").get("id").get("id"), criteria.ownerId().id()));
            if (criteria.sent()) {
                predicates.add(cb.equal(root.get("folder"), EmailMessage.FOLDER_SENT));
            } else {
                predicates.add(cb.or(cb.isNull(root.get("folder")), cb.notEqual(root.get("folder"), EmailMessage.FOLDER_SENT)));
            }
            if (criteria.accountId() != null) {
                predicates.add(cb.equal(account.get("id").get("id"), criteria.accountId().id()));
            }
            if (criteria.labelId() != null) {
                Subquery<Long> labelled = query.subquery(Long.class);
                Root<EmailLabel> emailLabel = labelled.from(EmailLabel.class);
                labelled.select(emailLabel.get("id").get("emailId")).where(
                        cb.equal(emailLabel.get("id").get("emailId"), root.get("id").get("id")),
                        cb.equal(emailLabel.get("id").get("labelId"), criteria.labelId().id()));
                predicates.add(cb.exists(labelled));
            }
            if (criteria.processingState() != null) {
                predicates.add(cb.equal(root.get("processingState"), criteria.processingState()));
            }
            if (criteria.read() != null) {
                predicates.add(cb.equal(root.get("read"), criteria.read()));
            }
            if (criteria.answered() != null && !criteria.sent()) {
                // Answered: a sent message of the same conversation in the same mailbox, newer than this email.
                Subquery<Long> reply = query.subquery(Long.class);
                Root<EmailMessage> sent = reply.from(EmailMessage.class);
                reply.select(sent.get("id").get("id")).where(
                        cb.equal(sent.get("account"), root.get("account")),
                        cb.equal(sent.get("conversationId"), root.get("conversationId")),
                        cb.equal(sent.get("folder"), EmailMessage.FOLDER_SENT),
                        cb.greaterThan(sent.<java.time.Instant>get("receivedAt"), root.<java.time.Instant>get("receivedAt")));
                predicates.add(criteria.answered() ? cb.exists(reply) : cb.not(cb.exists(reply)));
            }
            if (criteria.text() != null && !criteria.text().isBlank()) {
                String pattern = "%" + escapeLike(criteria.text().trim().toLowerCase(Locale.ROOT)) + "%";
                SearchField field = criteria.field() != null ? criteria.field() : SearchField.ALL;
                List<Predicate> any = new ArrayList<>();
                if (field == SearchField.ALL || field == SearchField.SUBJECT) {
                    any.add(cb.like(cb.lower(root.get("subject")), pattern, ESCAPE));
                }
                if (field == SearchField.ALL || field == SearchField.PEOPLE) {
                    any.add(cb.like(cb.lower(root.get("fromAddress")), pattern, ESCAPE));
                    any.add(cb.like(cb.lower(root.get("fromName")), pattern, ESCAPE));
                    // Recipients are jsonb lists of {name, address}: searched as their JSON text.
                    any.add(cb.like(cb.lower(cb.function("text", String.class, root.get("toRecipients"))), pattern, ESCAPE));
                    any.add(cb.like(cb.lower(cb.function("text", String.class, root.get("ccRecipients"))), pattern, ESCAPE));
                }
                if (field == SearchField.ALL || field == SearchField.CONTENT) {
                    any.add(cb.like(cb.lower(root.get("snippet")), pattern, ESCAPE));
                    any.add(cb.like(cb.lower(root.get("bodyText")), pattern, ESCAPE));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Emails a record comes up in: one of {@code emailIds} (record sources), sent from one of {@code addresses}, or
     * sent from an address at one of {@code domains} (whole domains: {@code shop.example} does not match
     * {@code myshop.example}). Matches nothing when all are empty. Combine with {@link #matching} for the owner scope.
     */
    public static Specification<EmailMessage> involving(Collection<Long> emailIds, Collection<String> addresses,
                                                        Collection<String> domains) {
        return (root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            if (!emailIds.isEmpty()) {
                any.add(root.get("id").get("id").in(emailIds));
            }
            if (!addresses.isEmpty()) {
                any.add(cb.lower(root.get("fromAddress")).in(addresses.stream().map(address -> address.toLowerCase(Locale.ROOT)).toList()));
            }
            for (String domain : domains) {
                any.add(cb.like(cb.lower(root.get("fromAddress")), "%@" + escapeLike(domain.toLowerCase(Locale.ROOT)), ESCAPE));
            }
            return any.isEmpty() ? cb.disjunction() : cb.or(any.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
