package com.example.contor.mapper;

import com.example.contor.api.model.Creator;
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.RelationOrigin;
import com.example.contor.api.model.RelationTypeDto;
import com.example.contor.domain.Collection;
import com.example.contor.domain.Relation;
import com.example.contor.domain.RelationType;

import java.util.LinkedHashMap;

public class RelationMapper {

    public static RelationTypeDto toDto(RelationType type) {
        if (type == null) return null;

        return new RelationTypeDto()
                .id(type.getId() != null ? type.getId().id() : null)
                .slug(type.getSlug() != null ? type.getSlug().value() : null)
                .name(type.getName())
                .inverseName(type.getInverseName())
                .fromCollectionId(collectionId(type.getFromCollection()))
                .toCollectionId(collectionId(type.getToCollection()))
                .directed(type.isDirected())
                .attributesSchema(type.getAttributesSchema() != null ? new LinkedHashMap<>(type.getAttributesSchema()) : new LinkedHashMap<>())
                .createdBy(type.getCreatedBy() != null ? Creator.valueOf(type.getCreatedBy().name()) : null);
    }

    public static RelationDto toDto(Relation relation) {
        if (relation == null) return null;

        return new RelationDto()
                .id(relation.getId() != null ? relation.getId().id() : null)
                .relationTypeId(relation.getRelationType() != null && relation.getRelationType().getId() != null
                        ? relation.getRelationType().getId().id() : null)
                .fromCollectionId(collectionId(relation.getFromCollection()))
                .fromRecordId(relation.getFromRecordId())
                .toCollectionId(collectionId(relation.getToCollection()))
                .toRecordId(relation.getToRecordId())
                .attributes(relation.getAttributes() != null ? new LinkedHashMap<>(relation.getAttributes()) : new LinkedHashMap<>())
                .sourceEmailId(relation.getSourceEmail() != null && relation.getSourceEmail().getId() != null
                        ? relation.getSourceEmail().getId().id() : null)
                .confidence(MapperSupport.normalize(relation.getConfidence()))
                .validFrom(MapperSupport.toOffset(relation.getValidFrom()))
                .validTo(MapperSupport.toOffset(relation.getValidTo()))
                .createdBy(relation.getCreatedBy() != null ? RelationOrigin.valueOf(relation.getCreatedBy().name()) : null);
    }

    private static Long collectionId(Collection collection) {
        return collection != null && collection.getId() != null ? collection.getId().id() : null;
    }
}
