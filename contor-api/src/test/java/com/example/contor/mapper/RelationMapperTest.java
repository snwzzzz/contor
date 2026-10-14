package com.example.contor.mapper;

import com.example.contor.api.model.Creator;
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.RelationOrigin;
import com.example.contor.api.model.RelationTypeDto;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Relation;
import com.example.contor.domain.RelationCreatedBy;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class RelationMapperTest {

    private static Collection collection(long id) {
        return Collection.builder().id(new Collection.CollectionId(id)).build();
    }

    @Test
    void maps_relation_type_to_dto() {
        var type = RelationType.builder().id(new RelationType.RelationTypeId(3L)).slug(new Slug("works_at")).name("works at")
                .inverseName("employs").fromCollection(collection(1L)).toCollection(collection(2L)).directed(true)
                .attributesSchema(Map.of("since", "date")).createdBy(CreatedBy.USER).build();

        RelationTypeDto dto = RelationMapper.toDto(type);

        assertEquals(3L, dto.getId());
        assertEquals("works_at", dto.getSlug());
        assertEquals("employs", dto.getInverseName());
        assertEquals(1L, dto.getFromCollectionId());
        assertEquals(2L, dto.getToCollectionId());
        assertTrue(dto.getDirected());
        assertEquals("date", dto.getAttributesSchema().get("since"));
        assertEquals(Creator.USER, dto.getCreatedBy());
    }

    @Test
    void maps_relation_type_without_collection_restrictions() {
        RelationTypeDto dto = RelationMapper.toDto(RelationType.builder().slug(new Slug("related")).name("related").build());

        assertNull(dto.getFromCollectionId());
        assertNull(dto.getToCollectionId());
    }

    @Test
    void maps_relation_to_dto() {
        var relation = Relation.builder().id(new Relation.RelationId(7L))
                .relationType(RelationType.builder().id(new RelationType.RelationTypeId(3L)).build())
                .fromCollection(collection(1L)).fromRecordId(10L).toCollection(collection(2L)).toRecordId(20L)
                .attributes(Map.of("since", "2024")).sourceEmail(EmailMessage.builder().id(new EmailMessage.EmailMessageId(99L)).build())
                .confidence(new BigDecimal("0.9")).validFrom(Instant.parse("2024-01-01T00:00:00Z")).createdBy(RelationCreatedBy.AI).build();

        RelationDto dto = RelationMapper.toDto(relation);

        assertEquals(7L, dto.getId());
        assertEquals(3L, dto.getRelationTypeId());
        assertEquals(1L, dto.getFromCollectionId());
        assertEquals(10L, dto.getFromRecordId());
        assertEquals(2L, dto.getToCollectionId());
        assertEquals(20L, dto.getToRecordId());
        assertEquals("2024", dto.getAttributes().get("since"));
        assertEquals(99L, dto.getSourceEmailId());
        assertEquals(new BigDecimal("0.9"), dto.getConfidence());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z").atOffset(ZoneOffset.UTC), dto.getValidFrom());
        assertNull(dto.getValidTo());
        assertEquals(RelationOrigin.AI, dto.getCreatedBy());
    }

    @Test
    void handles_null_input_and_relations() {
        assertNull(RelationMapper.toDto((Relation) null));
        assertNull(RelationMapper.toDto((RelationType) null));
        RelationDto dto = RelationMapper.toDto(Relation.builder().build());
        assertNull(dto.getId());
        assertNull(dto.getRelationTypeId());
        assertNull(dto.getSourceEmailId());
    }
}
