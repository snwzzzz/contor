package com.example.contor.proposal;

import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.domain.ApiFieldMapping;
import com.example.contor.domain.Collection;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.MappingState;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.externalapi.ExternalApiMappingActivated;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.ExternalApiMappingRepository;
import com.example.contor.service.CollectionSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies MAP_API_ENDPOINT {@code {"mappingId", "endpointId", "collectionId", "titlePath", "fields": [{"fieldSlug",
 * "path"}]}}: the approved (possibly edited) mapping becomes ACTIVE and the endpoint's other mapping OFF. After the
 * decision commits, {@link ExternalApiMappingActivated} lets the ingest turn the endpoint's current items into records.
 */
@RequiredArgsConstructor
@Component
public class MapApiEndpointApplier implements ProposalApplier {

    private final ExternalApiMappingRepository externalApiMappingRepository;
    private final CollectionSchemaService collectionSchemaService;
    private final CollectionRepository collectionRepository;
    private final ApplicationEventPublisher events;

    @Override
    public ProposalKind kind() {
        return ProposalKind.MAP_API_ENDPOINT;
    }

    @Override
    public Map<String, Object> apply(Proposal proposal) {
        Map<String, Object> payload = proposal.getFinalPayload();
        Long mappingId = ProposalPayload.requiredId(payload, "mappingId");
        Long endpointId = ProposalPayload.requiredId(payload, "endpointId");
        Long collectionId = ProposalPayload.requiredId(payload, "collectionId");
        ExternalApiMapping mapping = externalApiMappingRepository.findById(new ExternalApiMapping.ExternalApiMappingId(mappingId))
                .orElseThrow(() -> new IllegalArgumentException("payload.mappingId: the mapping no longer exists"));
        if (!mapping.getEndpoint().getId().id().equals(endpointId)) {
            throw new IllegalArgumentException("payload.endpointId does not match the mapping");
        }

        JsonPathExpr titlePath = titlePath(ProposalPayload.optionalString(payload, "titlePath"));
        Set<String> slugs = collectionSchemaService.findById(collectionId).getFields().stream().map(CollectionFieldDto::getSlug)
                .collect(Collectors.toSet());
        List<ApiFieldMapping> fields = fields(payload.get("fields"), slugs);
        if (titlePath == null && fields.isEmpty()) throw new IllegalArgumentException("payload: map at least the title or one field");

        mapping.setCollection(collectionRepository.getReferenceById(new Collection.CollectionId(collectionId)));
        mapping.setTitlePath(titlePath);
        mapping.setFieldMap(new ArrayList<>(fields));
        mapping.setState(MappingState.ACTIVE);
        List<ExternalApiMapping> changed = new ArrayList<>(List.of(mapping));
        for (ExternalApiMapping other : externalApiMappingRepository.findByEndpoint_Id(mapping.getEndpoint().getId())) {
            if (other != mapping && other.getState() == MappingState.ACTIVE) {
                other.setState(MappingState.OFF);
                changed.add(other);
            }
        }
        externalApiMappingRepository.saveAllAndFlush(changed);
        events.publishEvent(new ExternalApiMappingActivated(endpointId));
        return Map.of("mappingId", mappingId, "endpointId", endpointId);
    }

    private static JsonPathExpr titlePath(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new JsonPathExpr(value.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("payload.titlePath is not a valid JSON path");
        }
    }

    private static List<ApiFieldMapping> fields(Object raw, Set<String> slugs) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("payload.fields must be a list");
        List<ApiFieldMapping> fields = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> field) || !(field.get("fieldSlug") instanceof String slug) || !(field.get("path") instanceof String path)) {
                throw new IllegalArgumentException("payload.fields: every field needs fieldSlug and path");
            }
            if (!slugs.contains(slug)) throw new IllegalArgumentException("payload.fields: the collection has no field " + slug);
            fields.add(new ApiFieldMapping(slug, path));
        }
        return fields;
    }
}
