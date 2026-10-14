package com.example.contor.persistence;

import com.example.contor.ai.DigestEmail;
import com.example.contor.ai.DiscoveryEmail;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.ProcessingState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, EmailMessage.EmailMessageId>,
        JpaSpecificationExecutor<EmailMessage>, EmailSummaryQueries {

    Optional<EmailMessage> findByAccount_IdAndProviderMessageId(MailAccount.MailAccountId accountId, String providerMessageId);

    List<EmailMessage> findByProcessingState(ProcessingState processingState);

    /** Ids of an owner's emails in {@code state} received at/after {@code since}, oldest first (AI processing order). */
    @Query("select e.id.id from EmailMessage e where e.account.owner.id = :ownerId and e.processingState = :state "
            + "and e.receivedAt >= :since order by e.receivedAt asc")
    List<Long> findIdsForProcessing(AppUser.AppUserId ownerId, ProcessingState state, Instant since, Pageable pageable);

    /** Sender/subject/snippet of an owner's emails received at/after {@code since}, newest first (category discovery). */
    @Query("select new com.example.contor.ai.DiscoveryEmail(e.fromName, e.fromAddress, e.subject, e.snippet, e.receivedAt) "
            + "from EmailMessage e where e.account.owner.id = :ownerId and e.receivedAt >= :since "
            + "and (e.folder is null or e.folder <> 'sent') order by e.receivedAt desc")
    List<DiscoveryEmail> findDiscoveryEmails(AppUser.AppUserId ownerId, Instant since, Pageable pageable);

    /** Ids of an owner's inbox emails that have no vector of {@code model} yet, newest first (embedding backfill). */
    @Query("select e.id.id from EmailMessage e where e.account.owner.id = :ownerId and (e.folder is null or e.folder <> 'sent') "
            + "and not exists (select 1 from EmailEmbedding x where x.id.emailId = e.id.id and x.id.model = :model) "
            + "order by e.receivedAt desc")
    List<Long> findIdsWithoutEmbedding(AppUser.AppUserId ownerId, String model, Pageable pageable);

    /** Senders of an owner's inbox whose name or address matches the (lower-case LIKE) {@code pattern}, most emails first. */
    @Query("select new com.example.contor.persistence.SenderCount(max(e.fromName), lower(e.fromAddress), count(e)) "
            + "from EmailMessage e where e.account.owner.id = :ownerId and (e.folder is null or e.folder <> 'sent') "
            + "and e.fromAddress is not null "
            + "and (lower(e.fromAddress) like :pattern escape '\\' or lower(e.fromName) like :pattern escape '\\') "
            + "group by lower(e.fromAddress) order by count(e) desc, lower(e.fromAddress)")
    List<SenderCount> findSenders(AppUser.AppUserId ownerId, String pattern, Pageable pageable);

    /** One recipient of an owner's sent mail (native query row, see {@link #findRecipients}). */
    interface RecipientRow {
        String getName();

        String getAddress();

        Long getEmails();
    }

    /** Recipients of an owner's sent mail matching the (lower-case LIKE) {@code pattern}: unnests the jsonb To list. */
    @Query(value = "select max(r.name) as name, lower(r.address) as address, count(*) as emails "
            + "from \"email_message\" e join \"mail_account\" a on a.id = e.account_id "
            + "cross join lateral jsonb_to_recordset(e.to_recipients) as r(name text, address text) "
            + "where a.owner_user_id = :ownerId and e.folder = 'sent' and r.address is not null "
            + "and (lower(r.address) like :pattern escape '\\' or lower(r.name) like :pattern escape '\\') "
            + "group by lower(r.address) order by count(*) desc, lower(r.address) limit :limit", nativeQuery = true)
    List<RecipientRow> findRecipientRows(Long ownerId, String pattern, int limit);

    /** The people an owner wrote to whose name or address matches {@code pattern}, most emails first (sent-mail suggestions). */
    default List<SenderCount> findRecipients(AppUser.AppUserId ownerId, String pattern, int limit) {
        return findRecipientRows(ownerId.id(), pattern, limit).stream()
                .map(row -> new SenderCount(row.getName(), row.getAddress(), row.getEmails()))
                .toList();
    }

    /** An owner's inbox emails from {@code address} (any case) received before {@code before}, newest first. */
    @Query("select e from EmailMessage e where e.account.owner.id = :ownerId and lower(e.fromAddress) = lower(:address) "
            + "and (e.folder is null or e.folder <> 'sent') and e.receivedAt < :before order by e.receivedAt desc")
    List<EmailMessage> findFromSenderBefore(AppUser.AppUserId ownerId, String address, Instant before, Pageable pageable);

    /**
     * Ids of an owner's sent mail from [{@code from}, {@code to}) that has no later received message in its conversation
     * (nobody answered yet), newest first. Mail without a conversation id counts as unanswered.
     */
    @Query(value = "select s.id from \"email_message\" s join \"mail_account\" a on a.id = s.account_id "
            + "where a.owner_user_id = :ownerId and s.folder = 'sent' and s.received_at >= :from and s.received_at < :to "
            + "and not exists (select 1 from \"email_message\" r where r.account_id = s.account_id "
            + "and r.conversation_id = s.conversation_id and (r.folder is null or r.folder <> 'sent') and r.received_at > s.received_at) "
            + "order by s.received_at desc limit :limit", nativeQuery = true)
    List<Long> findUnansweredSentIds(Long ownerId, Instant from, Instant to, int limit);

    default List<Long> findUnansweredSent(AppUser.AppUserId ownerId, Instant from, Instant to, int limit) {
        return findUnansweredSentIds(ownerId.id(), from, to, limit);
    }

    /** An owner's mail (inbox and sent) received since {@code since}, newest first, as digest lines (insight pass). */
    @Query("select new com.example.contor.ai.DigestEmail(e.id.id, e.fromName, e.fromAddress, e.subject, e.snippet, e.receivedAt, e.folder) "
            + "from EmailMessage e where e.account.owner.id = :ownerId and e.receivedAt >= :since order by e.receivedAt desc")
    List<DigestEmail> findDigest(AppUser.AppUserId ownerId, Instant since, Pageable pageable);

    /** Whether an owner ever sent mail to {@code address} (any case, To recipients of Sent Items). */
    @Query(value = "select exists (select 1 from \"email_message\" e join \"mail_account\" a on a.id = e.account_id "
            + "cross join lateral jsonb_array_elements(e.to_recipients) r "
            + "where a.owner_user_id = :ownerId and e.folder = 'sent' and lower(r ->> 'address') = lower(:address))", nativeQuery = true)
    boolean existsSentToRow(Long ownerId, String address);

    default boolean existsSentTo(AppUser.AppUserId ownerId, String address) {
        return existsSentToRow(ownerId.id(), address);
    }

    /** All messages of one conversation in a mailbox (sent mail included), oldest first. */
    @Query("select e from EmailMessage e where e.account.id = :accountId and e.conversationId = :conversationId "
            + "order by e.receivedAt asc, e.id.id asc")
    List<EmailMessage> findThread(MailAccount.MailAccountId accountId, String conversationId);

    /** Message count and last reply per conversation, for the conversations of a page of emails (one query, no N+1). */
    @Query("select new com.example.contor.persistence.ThreadStat(e.account.id.id, e.conversationId, count(e), "
            + "max(case when e.folder = 'sent' then e.receivedAt else null end)) "
            + "from EmailMessage e where e.account.owner.id = :ownerId and e.conversationId in :conversationIds "
            + "group by e.account.id.id, e.conversationId")
    List<ThreadStat> threadStats(AppUser.AppUserId ownerId, Collection<String> conversationIds);

    /** An owner's inbox emails: what semantic search indexes. */
    @Query("select count(e) from EmailMessage e where e.account.owner.id = :ownerId and (e.folder is null or e.folder <> 'sent')")
    long countIndexable(AppUser.AppUserId ownerId);

    /** Moves an owner's emails received before {@code before} from one processing state to another. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailMessage e set e.processingState = :to where e.processingState = :from and e.receivedAt < :before "
            + "and e.account in (select a from MailAccount a where a.owner.id = :ownerId)")
    int updateProcessingStateBefore(AppUser.AppUserId ownerId, ProcessingState from, ProcessingState to, Instant before);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailMessage e set e.processingState = :to where e.processingState = :from")
    int updateProcessingState(ProcessingState from, ProcessingState to);
}
