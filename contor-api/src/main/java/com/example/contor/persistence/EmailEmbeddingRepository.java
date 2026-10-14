package com.example.contor.persistence;

import com.example.contor.domain.EmailEmbedding;
import com.example.contor.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EmailEmbeddingRepository extends JpaRepository<EmailEmbedding, EmailEmbedding.EmailEmbeddingId> {

    List<EmailEmbedding> findById_EmailId(Long emailId);

    /** How many of an owner's inbox emails (sent mail is not indexed) have a vector of {@code model}. */
    @Query("select count(x) from EmailEmbedding x, EmailMessage e where x.id.emailId = e.id.id and x.id.model = :model "
            + "and e.account.owner.id = :ownerId and (e.folder is null or e.folder <> 'sent')")
    long countIndexed(AppUser.AppUserId ownerId, String model);

    /** One row of {@link #findNearestRows}. */
    interface NearRow {
        Long getEmailId();

        Double getDistance();
    }

    @Query(value = "select x.email_id as emailId, (x.embedding <=> q.embedding) as distance "
            + "from \"email_embedding\" q "
            + "join \"email_embedding\" x on x.model = q.model and x.email_id <> q.email_id "
            + "join \"email_message\" e on e.id = x.email_id "
            + "join \"mail_account\" a on a.id = e.account_id "
            + "where q.email_id = :emailId and q.model = :model and a.owner_user_id = :ownerId "
            + "and (e.folder is null or e.folder <> 'sent') and e.received_at >= :since and e.received_at < :before "
            + "and (x.embedding <=> q.embedding) <= :maxDistance "
            + "order by x.embedding <=> q.embedding limit :limit", nativeQuery = true)
    List<NearRow> findNearestRows(Long ownerId, Long emailId, String model, Instant since, Instant before, double maxDistance,
                                  int limit);

    /**
     * An owner's inbox emails received in [{@code since}, {@code before}) whose {@code model} vector is within {@code maxDistance}
     * (cosine) of the email's own vector, closest first. Empty when the email has no vector of that model.
     */
    default List<NearEmail> findNearest(AppUser.AppUserId ownerId, Long emailId, String model, Instant since, Instant before,
                                        double maxDistance, int limit) {
        return findNearestRows(ownerId.id(), emailId, model, since, before, maxDistance, limit).stream()
                .map(row -> new NearEmail(row.getEmailId(), row.getDistance()))
                .toList();
    }

    /** Drops the vectors of every other model (after a backfill with the current one completed). */
    void deleteById_ModelNot(String model);
}
