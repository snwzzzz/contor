package com.example.contor.persistence;

import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Proposal.ProposalId> {

    List<Proposal> findByStatusOrderByCreatedAtDesc(ProposalStatus status);

    /** Every proposal that came from one of the given emails (AI evaluation export). */
    List<Proposal> findBySourceEmail_IdIn(Collection<EmailMessage.EmailMessageId> emailIds);

    long countByStatus(ProposalStatus status);

    Page<Proposal> findByStatus(ProposalStatus status, Pageable pageable);

    /**
     * Findings = data the AI extracted: record proposals outside the action collections (calendar, to-dos).
     * Actions = everything else. {@code actionCollectionIds} must not be empty (pass -1 when none exist).
     */
    String FINDING = "kind in ('CREATE_RECORD', 'UPDATE_RECORD') "
            + "and coalesce((payload ->> 'collectionId')::bigint, -1) not in (:actionCollectionIds)";

    @Query(value = "select * from proposal where status = :status and " + FINDING + " order by created_at desc",
            countQuery = "select count(*) from proposal where status = :status and " + FINDING,
            nativeQuery = true)
    Page<Proposal> findFindings(String status, Collection<Long> actionCollectionIds, Pageable pageable);

    @Query(value = "select count(*) from proposal where status = :status and " + FINDING, nativeQuery = true)
    long countFindings(String status, Collection<Long> actionCollectionIds);

    @Query(value = "select * from proposal where status = :status and not (" + FINDING + ") order by created_at desc",
            countQuery = "select count(*) from proposal where status = :status and not (" + FINDING + ")",
            nativeQuery = true)
    Page<Proposal> findActions(String status, Collection<Long> actionCollectionIds, Pageable pageable);

    @Query(value = "select count(*) from proposal where status = :status and not (" + FINDING + ")", nativeQuery = true)
    long countActions(String status, Collection<Long> actionCollectionIds);

    /** A pending CREATE_RECORD proposal for the collection with this title (case-insensitive) exists. */
    @Query(value = "select exists (select 1 from proposal where status = 'PENDING' and kind = 'CREATE_RECORD' "
            + "and (payload ->> 'collectionId')::bigint = :collectionId and lower(payload ->> 'title') = lower(:title))",
            nativeQuery = true)
    boolean existsPendingCreateRecordWithTitle(Long collectionId, String title);

    /** A pending CREATE_RECORD proposal for the collection with this field value (case-insensitive) exists. */
    @Query(value = "select exists (select 1 from proposal where status = 'PENDING' and kind = 'CREATE_RECORD' "
            + "and (payload ->> 'collectionId')::bigint = :collectionId and lower(payload -> 'values' ->> :key) = lower(:value))",
            nativeQuery = true)
    boolean existsPendingCreateRecordWithValue(Long collectionId, String key, String value);
}
