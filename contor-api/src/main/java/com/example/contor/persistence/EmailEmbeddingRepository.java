package com.example.contor.persistence;

import com.example.contor.domain.EmailEmbedding;
import com.example.contor.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailEmbeddingRepository extends JpaRepository<EmailEmbedding, EmailEmbedding.EmailEmbeddingId> {

    List<EmailEmbedding> findById_EmailId(Long emailId);

    /** How many of an owner's inbox emails (sent mail is not indexed) have a vector of {@code model}. */
    @Query("select count(x) from EmailEmbedding x, EmailMessage e where x.id.emailId = e.id.id and x.id.model = :model "
            + "and e.account.owner.id = :ownerId and (e.folder is null or e.folder <> 'sent')")
    long countIndexed(AppUser.AppUserId ownerId, String model);

    /** Drops the vectors of every other model (after a backfill with the current one completed). */
    void deleteById_ModelNot(String model);
}
