package com.example.contor.persistence;

import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalApiEndpointRepository extends JpaRepository<ExternalApiEndpoint, ExternalApiEndpoint.ExternalApiEndpointId> {

    List<ExternalApiEndpoint> findByApi_IdOrderByNameAsc(ExternalApi.ExternalApiId apiId);

    /** The endpoints a poll calls. */
    List<ExternalApiEndpoint> findByApi_IdAndEnabledTrue(ExternalApi.ExternalApiId apiId);

    /** An endpoint only when it belongs to {@code apiId}. */
    Optional<ExternalApiEndpoint> findByIdAndApi_Id(ExternalApiEndpoint.ExternalApiEndpointId id, ExternalApi.ExternalApiId apiId);
}
