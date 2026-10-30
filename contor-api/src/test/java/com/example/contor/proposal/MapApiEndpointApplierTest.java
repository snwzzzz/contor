package com.example.contor.proposal;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.domain.ApiFieldMapping;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.Collection;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.MappingState;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.externalapi.ExternalApiMappingActivated;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.ExternalApiMappingRepository;
import com.example.contor.service.CollectionSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MapApiEndpointApplierTest {

    private static final ExternalApiEndpoint.ExternalApiEndpointId ORDERS_ID = new ExternalApiEndpoint.ExternalApiEndpointId(30L);

    @Mock
    private ExternalApiMappingRepository externalApiMappingRepository;
    @Mock
    private CollectionSchemaService collectionSchemaService;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private MapApiEndpointApplier applier;

    private ExternalApiMapping proposed;
    private ExternalApiMapping active;

    @BeforeEach
    void setUp() {
        var orders = ExternalApiEndpoint.builder().id(ORDERS_ID).name("Orders").path(new ApiPath("/orders")).build();
        proposed = ExternalApiMapping.builder().id(new ExternalApiMapping.ExternalApiMappingId(40L)).endpoint(orders)
                .collection(Collection.builder().id(new Collection.CollectionId(4L)).build()).state(MappingState.PROPOSED).build();
        active = ExternalApiMapping.builder().id(new ExternalApiMapping.ExternalApiMappingId(41L)).endpoint(orders)
                .collection(Collection.builder().id(new Collection.CollectionId(5L)).build()).state(MappingState.ACTIVE).build();
        when(externalApiMappingRepository.findById(proposed.getId())).thenReturn(Optional.of(proposed));
        when(externalApiMappingRepository.findByEndpoint_Id(ORDERS_ID)).thenReturn(List.of(proposed, active));
        when(collectionSchemaService.findById(4L)).thenReturn(collection(4L, "total", "customer"));
        when(collectionSchemaService.findById(6L)).thenReturn(collection(6L, "amount"));
        when(collectionRepository.getReferenceById(any())).thenAnswer(inv -> Collection.builder().id(inv.getArgument(0)).build());
    }

    private static CollectionDto collection(long id, String... fields) {
        var dto = new CollectionDto().id(id).slug("c" + id).name("C" + id);
        for (String field : fields) {
            dto.addFieldsItem(new CollectionFieldDto(1L, field, field, CollectionFieldType.TEXT, new LinkedHashMap<>(), false, false, 0));
        }
        return dto;
    }

    private static Proposal proposal(Map<String, Object> finalPayload) {
        return Proposal.builder().kind(ProposalKind.MAP_API_ENDPOINT).payload(finalPayload).finalPayload(finalPayload).build();
    }

    private static Map<String, Object> payload(long collectionId, String titlePath, List<Map<String, Object>> fields) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mappingId", 40);
        payload.put("endpointId", 30);
        payload.put("collectionId", collectionId);
        if (titlePath != null) payload.put("titlePath", titlePath);
        payload.put("fields", fields);
        return payload;
    }

    @Test
    void maps_api_endpoints_without_the_owner() {
        assertThat(applier.kind()).isEqualTo(ProposalKind.MAP_API_ENDPOINT);
        assertThat(applier.requiresOwner()).isFalse();
    }

    @Test
    void activates_the_mapping_as_approved_and_switches_the_endpoints_other_mapping_off() {
        var result = applier.apply(proposal(payload(4L, "$.number", List.of(Map.of("fieldSlug", "customer", "path", "$.customer.name")))));

        assertThat(proposed.getState()).isEqualTo(MappingState.ACTIVE);
        assertThat(proposed.getTitlePath()).isEqualTo(new JsonPathExpr("$.number"));
        assertThat(proposed.getFieldMap()).containsExactly(new ApiFieldMapping("customer", "$.customer.name"));
        assertThat(active.getState()).isEqualTo(MappingState.OFF);
        verify(externalApiMappingRepository).saveAllAndFlush(List.of(proposed, active));
        verify(events).publishEvent(new ExternalApiMappingActivated(30L));
        assertThat(result).isEqualTo(Map.of("mappingId", 40L, "endpointId", 30L));
    }

    @Test
    void the_approved_payload_may_pick_another_collection() {
        applier.apply(proposal(payload(6L, null, List.of(Map.of("fieldSlug", "amount", "path", "$.total")))));

        assertThat(proposed.getCollection().getId()).isEqualTo(new Collection.CollectionId(6L));
        assertThat(proposed.getTitlePath()).isNull();
    }

    @Test
    void rejects_fields_the_collection_lacks_invalid_paths_and_empty_mappings() {
        assertThatThrownBy(() -> applier.apply(proposal(payload(4L, "$.n", List.of(Map.of("fieldSlug", "amount", "path", "$.total"))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("payload.fields: the collection has no field amount");
        assertThatThrownBy(() -> applier.apply(proposal(payload(4L, "$.n", List.of(Map.of("fieldSlug", "total", "path", "$..total"))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applier.apply(proposal(payload(4L, "$..n", List.of()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("payload.titlePath is not a valid JSON path");
        assertThatThrownBy(() -> applier.apply(proposal(payload(4L, null, List.of()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("payload: map at least the title or one field");
        assertThatThrownBy(() -> applier.apply(proposal(payload(4L, "$.n", List.of(Map.of("fieldSlug", "total"))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("payload.fields: every field needs fieldSlug and path");
        verify(externalApiMappingRepository, never()).saveAllAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void rejects_a_mapping_that_is_gone_or_belongs_to_another_endpoint() {
        Map<String, Object> other = new HashMap<>(payload(4L, "$.n", new ArrayList<>()));
        other.put("mappingId", 99);
        assertThatThrownBy(() -> applier.apply(proposal(other))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payload.mappingId: the mapping no longer exists");

        Map<String, Object> elsewhere = new HashMap<>(payload(4L, "$.n", new ArrayList<>()));
        elsewhere.put("endpointId", 31);
        assertThatThrownBy(() -> applier.apply(proposal(elsewhere))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payload.endpointId does not match the mapping");
    }
}
