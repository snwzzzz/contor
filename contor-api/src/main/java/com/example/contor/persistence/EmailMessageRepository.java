package com.example.contor.persistence;

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
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, EmailMessage.EmailMessageId>,
        JpaSpecificationExecutor<EmailMessage> {

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
