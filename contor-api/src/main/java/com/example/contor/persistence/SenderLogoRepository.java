package com.example.contor.persistence;

import com.example.contor.domain.SenderDomain;
import com.example.contor.domain.SenderLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SenderLogoRepository extends JpaRepository<SenderLogo, SenderLogo.SenderLogoId> {

    Optional<SenderLogo> findByDomain(SenderDomain domain);

    List<SenderLogo> findByDomainIn(Collection<SenderDomain> domains);

    /**
     * Serialises writers of the logo cache until the current transaction ends, so concurrent requests for the same
     * domains do not insert the same row twice.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext('sender_logo'))) lock", nativeQuery = true)
    int lockWrites();
}
