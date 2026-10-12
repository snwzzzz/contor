package com.example.contor.persistence;

import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Proposal.ProposalId> {

    List<Proposal> findByStatusOrderByCreatedAtDesc(ProposalStatus status);

    /** Every proposal that came from one of the given emails (AI evaluation export). */
    List<Proposal> findBySourceEmail_IdIn(Collection<EmailMessage.EmailMessageId> emailIds);

    long countByStatus(ProposalStatus status);

    Page<Proposal> findByStatus(ProposalStatus status, Pageable pageable);

    Page<Proposal> findByStatusAndImportanceScoreGreaterThanEqual(ProposalStatus status, int minScore, Pageable pageable);

    Page<Proposal> findByImportanceScoreGreaterThanEqual(int minScore, Pageable pageable);

    long countByStatusAndImportanceScoreGreaterThanEqual(ProposalStatus status, int minScore);

    /** Pending proposals that were never scored (they existed before importance did). */
    List<Proposal> findByStatusAndImportanceReasonIsNull(ProposalStatus status);

    /**
     * Findings = data the AI extracted: record proposals outside the action collections (calendar, to-dos), and the
     * relations between them.
     * Actions = everything else. {@code actionCollectionIds} must not be empty (pass -1 when none exist).
     * {@code minScore} keeps proposals with at least that importance score; {@code byImportance} sorts by it (else newest first).
     */
    String FINDING = "(kind = 'CREATE_RELATION' or (kind in ('CREATE_RECORD', 'UPDATE_RECORD') "
            + "and coalesce((payload ->> 'collectionId')::bigint, -1) not in (:actionCollectionIds)))";

    String MIN_SCORE = " and importance_score >= :minScore";
    String ORDER = " order by (case when :byImportance then importance_score else 0 end) desc, created_at desc, id desc";

    @Query(value = "select * from proposal where status = :status and " + FINDING + MIN_SCORE + ORDER,
            countQuery = "select count(*) from proposal where status = :status and " + FINDING + MIN_SCORE,
            nativeQuery = true)
    Page<Proposal> findFindings(String status, Collection<Long> actionCollectionIds, int minScore, boolean byImportance, Pageable pageable);

    @Query(value = "select count(*) from proposal where status = :status and " + FINDING + MIN_SCORE, nativeQuery = true)
    long countFindings(String status, Collection<Long> actionCollectionIds, int minScore);

    @Query(value = "select * from proposal where status = :status and not (" + FINDING + ")" + MIN_SCORE + ORDER,
            countQuery = "select count(*) from proposal where status = :status and not (" + FINDING + ")" + MIN_SCORE,
            nativeQuery = true)
    Page<Proposal> findActions(String status, Collection<Long> actionCollectionIds, int minScore, boolean byImportance, Pageable pageable);

    @Query(value = "select count(*) from proposal where status = :status and not (" + FINDING + ")" + MIN_SCORE, nativeQuery = true)
    long countActions(String status, Collection<Long> actionCollectionIds, int minScore);

    /** Whether an INSIGHT with this key was ever proposed (pending, approved or dismissed): each insight is proposed once. */
    @Query(value = "select exists (select 1 from proposal where kind = 'INSIGHT' and payload ->> 'insightKey' = :key)", nativeQuery = true)
    boolean existsInsightKey(String key);

    /** Id of a pending CREATE_RECORD proposal for the collection with this title (case-insensitive), if there is one. */
    @Query(value = "select id from proposal where status = 'PENDING' and kind = 'CREATE_RECORD' "
            + "and (payload ->> 'collectionId')::bigint = :collectionId and lower(payload ->> 'title') = lower(:title) "
            + "order by id limit 1",
            nativeQuery = true)
    Optional<Long> findPendingCreateRecordIdWithTitle(Long collectionId, String title);

    /** Id of a pending CREATE_RECORD proposal for the collection with this field value (case-insensitive), if there is one. */
    @Query(value = "select id from proposal where status = 'PENDING' and kind = 'CREATE_RECORD' "
            + "and (payload ->> 'collectionId')::bigint = :collectionId and lower(payload -> 'values' ->> :key) = lower(:value) "
            + "order by id limit 1",
            nativeQuery = true)
    Optional<Long> findPendingCreateRecordIdWithValue(Long collectionId, String key, String value);

    /** Proposals of {@code kind} in {@code status} whose source is one of the emails (thread view, inbox markers). */
    List<Proposal> findByKindAndStatusAndSourceEmail_IdIn(ProposalKind kind, ProposalStatus status,
                                                          Collection<EmailMessage.EmailMessageId> emailIds);

    /** Proposals of {@code kind} in {@code status} for one conversation of a mailbox (via their source email). */
    @Query("select p from Proposal p join p.sourceEmail s where p.kind = :kind and p.status = :status "
            + "and s.account.id = :accountId and s.conversationId = :conversationId order by p.id")
    List<Proposal> findForConversation(ProposalKind kind, ProposalStatus status, MailAccount.MailAccountId accountId,
                                       String conversationId);

    String NEWER_IN_CONVERSATION = "select p from Proposal p join p.sourceEmail s where p.kind = :kind and p.status = :status "
            + "and s.account.id = :accountId and s.conversationId is not null and exists (select 1 from EmailMessage e "
            + "where e.account.id = s.account.id and e.conversationId = s.conversationId and e.receivedAt > s.receivedAt and ";

    /** Proposals of {@code kind} in {@code status} of a mailbox whose conversation has a newer message the user sent. */
    @Query(NEWER_IN_CONVERSATION + "e.folder = 'sent') order by p.id")
    List<Proposal> findWithNewerSent(ProposalKind kind, ProposalStatus status, MailAccount.MailAccountId accountId);

    /** Proposals of {@code kind} in {@code status} of a mailbox whose conversation has a newer received message. */
    @Query(NEWER_IN_CONVERSATION + "(e.folder is null or e.folder <> 'sent')) order by p.id")
    List<Proposal> findWithNewerReceived(ProposalKind kind, ProposalStatus status, MailAccount.MailAccountId accountId);
}
