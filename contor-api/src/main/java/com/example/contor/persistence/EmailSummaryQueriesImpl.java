package com.example.contor.persistence;

import com.example.contor.domain.EmailMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

/** Criteria queries selecting only the summary columns into {@link EmailSummary}; the count runs only when needed. */
@RequiredArgsConstructor
class EmailSummaryQueriesImpl implements EmailSummaryQueries {

    private final EntityManager entityManager;

    @Override
    public Page<EmailSummary> findSummaries(Specification<EmailMessage> specification, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmailSummary> query = cb.createQuery(EmailSummary.class);
        Root<EmailMessage> root = query.from(EmailMessage.class);
        query.select(cb.construct(EmailSummary.class,
                root.get("id").get("id"), root.get("account").get("id").get("id"), root.get("conversationId"), root.get("folder"),
                root.get("fromAddress"), root.get("fromName"), root.get("toRecipients"), root.get("subject"), root.get("snippet"),
                root.get("receivedAt"), root.get("read"), root.get("hasAttachments"), root.get("processingState")));
        Predicate where = specification.toPredicate(root, query, cb);
        if (where != null) {
            query.where(where);
        }
        query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, cb));

        TypedQuery<EmailSummary> typed = entityManager.createQuery(query);
        if (pageable.isPaged()) {
            typed.setFirstResult((int) pageable.getOffset());
            typed.setMaxResults(pageable.getPageSize());
        }
        List<EmailSummary> content = typed.getResultList();
        return PageableExecutionUtils.getPage(content, pageable, () -> count(specification));
    }

    private long count(Specification<EmailMessage> specification) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<EmailMessage> root = query.from(EmailMessage.class);
        query.select(cb.count(root));
        Predicate where = specification.toPredicate(root, query, cb);
        if (where != null) {
            query.where(where);
        }
        return entityManager.createQuery(query).getSingleResult();
    }
}
