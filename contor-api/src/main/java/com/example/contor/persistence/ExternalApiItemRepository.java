package com.example.contor.persistence;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExternalApiItemRepository extends JpaRepository<ExternalApiItem, Long> {

    Optional<ExternalApiItem> findByEndpoint_IdAndItemKey(ExternalApiEndpoint.ExternalApiEndpointId endpointId, String itemKey);

    /** Items still in the endpoint's last answer. */
    List<ExternalApiItem> findByEndpoint_IdAndGoneAtIsNull(ExternalApiEndpoint.ExternalApiEndpointId endpointId);

    /**
     * The insight digest: the owner's items that appeared or disappeared since {@code since}, of apps the assistant may
     * learn from, the latest change first (with endpoint and app).
     */
    @Query("select i from ExternalApiItem i join fetch i.endpoint e join fetch e.api a where a.owner.id = :owner and a.aiAnalysis = true "
            + "and (i.firstSeenAt >= :since or i.goneAt >= :since) order by coalesce(i.goneAt, i.firstSeenAt) desc, i.id desc")
    List<ExternalApiItem> findDigest(@Param("owner") AppUser.AppUserId owner, @Param("since") Instant since, Pageable page);
}
