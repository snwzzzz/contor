package com.example.contor.persistence;

import com.example.contor.domain.EmailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Email lists without loading the bodies (tens of kilobytes per email that a list row never shows). */
public interface EmailSummaryQueries {

    /** A page of the emails matching {@code specification}, sorted and paged by {@code pageable}, as summaries. */
    Page<EmailSummary> findSummaries(Specification<EmailMessage> specification, Pageable pageable);
}
