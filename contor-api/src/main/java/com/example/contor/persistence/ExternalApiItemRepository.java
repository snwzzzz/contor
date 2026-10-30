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

    long countByEndpoint_IdAndGoneAtIsNotNull(ExternalApiEndpoint.ExternalApiEndpointId endpointId);

    /**
     * The insight digest: the owner's items that appeared or disappeared since {@code since}, of apps the assistant may
     * learn from, the latest change first (with endpoint and app).
     */
    @Query("select i from ExternalApiItem i join fetch i.endpoint e join fetch e.api a where a.owner.id = :owner and a.aiAnalysis = true "
            + "and (i.firstSeenAt >= :since or i.goneAt >= :since) order by coalesce(i.goneAt, i.firstSeenAt) desc, i.id desc")
    List<ExternalApiItem> findDigest(@Param("owner") AppUser.AppUserId owner, @Param("since") Instant since, Pageable page);

    /**
     * Chat retrieval: the owner's current items without a record (records are found as records) of apps the assistant
     * may learn from, whose key or data matches {@code pattern} ({@code ILIKE}, already escaped), last seen first.
     */
    @Query(value = "select i.* from external_api_item i join external_api_endpoint e on e.id = i.endpoint_id "
            + "join external_api a on a.id = e.api_id where a.owner_id = :owner and a.ai_analysis and i.record_id is null "
            + "and i.gone_at is null and (i.item_key ilike :pattern or cast(i.data as text) ilike :pattern) "
            + "order by i.last_seen_at desc, i.id desc limit :limit", nativeQuery = true)
    List<ExternalApiItem> searchForChat(@Param("owner") Long owner, @Param("pattern") String pattern, @Param("limit") int limit);
}
