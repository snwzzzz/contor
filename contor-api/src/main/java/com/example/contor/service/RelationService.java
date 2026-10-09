package com.example.contor.service;

import com.example.contor.api.model.NewRelationCommand;
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.UpdateRelationCommand;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.Relation;
import com.example.contor.domain.RelationCreatedBy;
import com.example.contor.domain.RelationType;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.exception.RelationNotFoundException;
import com.example.contor.exception.RelationTypeNotFoundException;
import com.example.contor.mapper.RelationMapper;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationRepository;
import com.example.contor.persistence.RelationTypeRepository;
import com.example.contor.persistence.dynamic.DynamicRecordRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Relations between records, each end addressed as (collection, record id). Both records must exist and the
 * collections must match the relation type (an undirected type also accepts the reversed orientation).
 */
@RequiredArgsConstructor
@Service
public class RelationService {

    private final RelationRepository relationRepository;
    private final RelationTypeRepository relationTypeRepository;
    private final CollectionRepository collectionRepository;
    private final DynamicRecordRepository dynamicRecordRepository;

    @Transactional
    public RelationDto createRelation(NewRelationCommand command) {
        return create(command, RelationCreatedBy.USER, null, null);
    }

    /** A relation proposed by the AI (applied from a proposal): marked as AI, with the email it came from and the confidence. */
    @Transactional
    public RelationDto createAiRelation(NewRelationCommand command, EmailMessage sourceEmail, BigDecimal confidence) {
        return create(command, RelationCreatedBy.AI, sourceEmail, confidence);
    }

    private RelationDto create(NewRelationCommand command, RelationCreatedBy origin, EmailMessage sourceEmail, BigDecimal confidence) {
        RelationType type = relationTypeRepository.findById(new RelationType.RelationTypeId(command.getRelationTypeId()))
                .orElseThrow(() -> new RelationTypeNotFoundException(command.getRelationTypeId()));
        Collection from = collection(command.getFromCollectionId());
        Collection to = collection(command.getToCollectionId());

        if (!fits(type, from, to) && (type.isDirected() || !fits(type, to, from))) {
            throw new IllegalArgumentException("Relation type '" + type.getSlug().value() + "' connects '"
                    + name(type.getFromCollection()) + "' to '" + name(type.getToCollection()) + "'");
        }
        if (from.getId().equals(to.getId()) && command.getFromRecordId().equals(command.getToRecordId())) {
            throw new IllegalArgumentException("A record cannot be related to itself");
        }
        Instant validFrom = toInstant(command.getValidFrom());
        Instant validTo = toInstant(command.getValidTo());
        requireValidity(validFrom, validTo);
        requireRecord(from, command.getFromRecordId());
        requireRecord(to, command.getToRecordId());
        boolean exists = relationRepository.existsByRelationType_IdAndFromCollection_IdAndFromRecordIdAndToCollection_IdAndToRecordId(
                type.getId(), from.getId(), command.getFromRecordId(), to.getId(), command.getToRecordId())
                || !type.isDirected() && relationRepository.existsByRelationType_IdAndFromCollection_IdAndFromRecordIdAndToCollection_IdAndToRecordId(
                type.getId(), to.getId(), command.getToRecordId(), from.getId(), command.getFromRecordId());
        if (exists) {
            throw new DuplicateResourceException("These records are already related as '" + type.getSlug().value() + "'");
        }

        Relation relation = Relation.builder()
                .relationType(type)
                .fromCollection(from)
                .fromRecordId(command.getFromRecordId())
                .toCollection(to)
                .toRecordId(command.getToRecordId())
                .attributes(command.getAttributes() != null ? new LinkedHashMap<>(command.getAttributes()) : new LinkedHashMap<>())
                .validFrom(validFrom)
                .validTo(validTo)
                .createdBy(origin)
                .sourceEmail(sourceEmail)
                .confidence(confidence)
                .build();

        return RelationMapper.toDto(relationRepository.saveAndFlush(relation));
    }

    /** Outgoing and incoming relations of one record, ordered by id. */
    @Transactional
    public List<RelationDto> listRelations(Long collectionId, Long recordId) {
        Collection collection = collection(collectionId);
        List<Relation> relations = new ArrayList<>(relationRepository.findByFromCollection_IdAndFromRecordId(collection.getId(), recordId));
        relations.addAll(relationRepository.findByToCollection_IdAndToRecordId(collection.getId(), recordId));
        return relations.stream()
                .distinct()
                .sorted(Comparator.comparing(relation -> relation.getId().id()))
                .map(RelationMapper::toDto)
                .toList();
    }

    @Transactional
    public RelationDto findById(Long id) {
        return relationRepository.findById(new Relation.RelationId(id))
                .map(RelationMapper::toDto)
                .orElseThrow(() -> new RelationNotFoundException(id));
    }

    @Transactional
    public RelationDto updateRelation(Long id, UpdateRelationCommand command) {
        Relation relation = relationRepository.findById(new Relation.RelationId(id))
                .orElseThrow(() -> new RelationNotFoundException(id));

        Instant validFrom = command.getValidFrom() != null ? toInstant(command.getValidFrom()) : relation.getValidFrom();
        Instant validTo = command.getValidTo() != null ? toInstant(command.getValidTo()) : relation.getValidTo();
        requireValidity(validFrom, validTo);
        if (command.getAttributes() != null) {
            relation.setAttributes(new LinkedHashMap<>(command.getAttributes()));
        }
        relation.setValidFrom(validFrom);
        relation.setValidTo(validTo);

        return RelationMapper.toDto(relationRepository.saveAndFlush(relation));
    }

    @Transactional
    public void deleteRelation(Long id) {
        if (!relationRepository.existsById(new Relation.RelationId(id))) {
            throw new RelationNotFoundException(id);
        }
        relationRepository.deleteById(new Relation.RelationId(id));
    }

    private Collection collection(Long id) {
        return collectionRepository.findById(new Collection.CollectionId(id))
                .orElseThrow(() -> new CollectionNotFoundException(id));
    }

    private void requireRecord(Collection collection, Long recordId) {
        if (!dynamicRecordRepository.exists(collection.getTableName(), recordId)) {
            throw new RecordNotFoundException(collection.getId().id(), recordId);
        }
    }

    private static boolean fits(RelationType type, Collection from, Collection to) {
        return matches(type.getFromCollection(), from) && matches(type.getToCollection(), to);
    }

    private static boolean matches(Collection allowed, Collection actual) {
        return allowed == null || Objects.equals(allowed.getId(), actual.getId());
    }

    private static String name(Collection collection) {
        return collection != null ? collection.getSlug().value() : "any";
    }

    private static void requireValidity(Instant validFrom, Instant validTo) {
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo must not be before validFrom");
        }
    }

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant() : null;
    }
}
