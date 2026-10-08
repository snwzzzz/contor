package com.example.contor.persistence;

import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Proposal.ProposalId> {

    List<Proposal> findByStatusOrderByCreatedAtDesc(ProposalStatus status);

    long countByStatus(ProposalStatus status);

    Page<Proposal> findByStatus(ProposalStatus status, Pageable pageable);

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
