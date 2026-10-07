package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailLabel;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Label;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.ProcessingState;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Email search: always limited to one owner's mailboxes, optional account, label, state and text filters. */
public final class EmailSpecifications {

    private static final char ESCAPE = '\\';

    private EmailSpecifications() {
    }

    /** {@code null} filters are ignored; {@code ownerId} is mandatory. */
    public record Criteria(AppUser.AppUserId ownerId, MailAccount.MailAccountId accountId, Label.LabelId labelId,
                           ProcessingState processingState, String text) {
    }

    public static Specification<EmailMessage> matching(Criteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<EmailMessage, MailAccount> account = root.join("account");
            predicates.add(cb.equal(account.get("owner").get("id").get("id"), criteria.ownerId().id()));
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
            if (criteria.text() != null && !criteria.text().isBlank()) {
                String pattern = "%" + escapeLike(criteria.text().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("subject")), pattern, ESCAPE),
                        cb.like(cb.lower(root.get("fromAddress")), pattern, ESCAPE),
                        cb.like(cb.lower(root.get("fromName")), pattern, ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
