package com.example.contor.persistence;

import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalApiSnapshotRepository extends JpaRepository<ExternalApiSnapshot, Long> {

    Optional<ExternalApiSnapshot> findFirstByEndpoint_IdOrderByFetchedAtDesc(ExternalApiEndpoint.ExternalApiEndpointId endpointId);

    /** Newest first (to keep the last few). */
    List<ExternalApiSnapshot> findByEndpoint_IdOrderByFetchedAtDesc(ExternalApiEndpoint.ExternalApiEndpointId endpointId);

    /** Forgets an endpoint's answers (its next answer counts as new). */
    void deleteByEndpoint_Id(ExternalApiEndpoint.ExternalApiEndpointId endpointId);
}
