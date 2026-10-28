package com.example.contor.persistence;

import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalApiItemRepository extends JpaRepository<ExternalApiItem, Long> {

    Optional<ExternalApiItem> findByEndpoint_IdAndItemKey(ExternalApiEndpoint.ExternalApiEndpointId endpointId, String itemKey);

    /** Items still in the endpoint's last answer. */
    List<ExternalApiItem> findByEndpoint_IdAndGoneAtIsNull(ExternalApiEndpoint.ExternalApiEndpointId endpointId);
}
