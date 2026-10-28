package com.example.contor.persistence;

import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.MappingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalApiMappingRepository extends JpaRepository<ExternalApiMapping, ExternalApiMapping.ExternalApiMappingId> {

    List<ExternalApiMapping> findByEndpoint_Id(ExternalApiEndpoint.ExternalApiEndpointId endpointId);

    List<ExternalApiMapping> findByEndpoint_IdAndState(ExternalApiEndpoint.ExternalApiEndpointId endpointId, MappingState state);
}
