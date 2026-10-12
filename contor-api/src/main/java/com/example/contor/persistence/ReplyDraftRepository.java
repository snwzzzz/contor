package com.example.contor.persistence;

import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.ReplyDraft;
import com.example.contor.domain.ReplyDraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReplyDraftRepository extends JpaRepository<ReplyDraft, ReplyDraft.ReplyDraftId> {

    /** Drafts in {@code status} answering one of the emails (a thread's messages), oldest first. */
    List<ReplyDraft> findByEmail_IdInAndStatusOrderByCreatedAtAsc(Collection<EmailMessage.EmailMessageId> emailIds, ReplyDraftStatus status);

    /** Which of the emails have a draft in {@code status} (inbox markers for one page, one query). */
    @Query("select distinct d.email.id.id from ReplyDraft d where d.email.id.id in :emailIds and d.status = :status")
    List<Long> findEmailIdsWithStatus(Collection<Long> emailIds, ReplyDraftStatus status);
}
