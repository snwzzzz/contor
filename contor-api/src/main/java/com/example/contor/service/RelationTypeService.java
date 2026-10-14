package com.example.contor.service;

import com.example.contor.api.model.NewRelationTypeCommand;
import com.example.contor.api.model.RelationTypeDto;
import com.example.contor.api.model.UpdateRelationTypeCommand;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.Slug;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.RelationTypeNotFoundException;
import com.example.contor.exception.ResourceInUseException;
import com.example.contor.mapper.RelationMapper;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationRepository;
import com.example.contor.persistence.RelationTypeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

/** Relation types (graph edge kinds). A type restricted to collections only connects records of those collections. */
@RequiredArgsConstructor
@Service
public class RelationTypeService {

    private final RelationTypeRepository relationTypeRepository;
    private final RelationRepository relationRepository;
    private final CollectionRepository collectionRepository;

    @Transactional
    public RelationTypeDto createRelationType(NewRelationTypeCommand command) {
        Slug slug = new Slug(command.getSlug());
        if (relationTypeRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("A relation type with slug '" + slug.value() + "' already exists");
        }

        RelationType type = RelationType.builder()
                .slug(slug)
                .name(command.getName())
                .inverseName(command.getInverseName())
                .fromCollection(command.getFromCollectionId() != null ? collection(command.getFromCollectionId()) : null)
                .toCollection(command.getToCollectionId() != null ? collection(command.getToCollectionId()) : null)
                .directed(command.getDirected() == null || command.getDirected())
                .attributesSchema(command.getAttributesSchema() != null ? new LinkedHashMap<>(command.getAttributesSchema()) : new LinkedHashMap<>())
                .createdBy(CreatedBy.USER)
                .build();

        return RelationMapper.toDto(relationTypeRepository.saveAndFlush(type));
    }

    /** Relation types contor ships with (seeding). */
    @Transactional
    public void createSystemRelationType(String slug, String name, String inverseName,
                                         Long fromCollectionId, Long toCollectionId, boolean directed) {
        relationTypeRepository.saveAndFlush(RelationType.builder()
                .slug(new Slug(slug))
                .name(name)
                .inverseName(inverseName)
                .fromCollection(collection(fromCollectionId))
                .toCollection(collection(toCollectionId))
                .directed(directed)
                .createdBy(CreatedBy.USER)
                .build());
    }

    @Transactional
    public List<RelationTypeDto> findAll() {
        return relationTypeRepository.findAllByOrderByNameAsc().stream()
                .map(RelationMapper::toDto)
                .toList();
    }

    @Transactional
    public RelationTypeDto findById(Long id) {
        return relationTypeRepository.findById(new RelationType.RelationTypeId(id))
                .map(RelationMapper::toDto)
                .orElseThrow(() -> new RelationTypeNotFoundException(id));
    }

    @Transactional
    public RelationTypeDto updateRelationType(Long id, UpdateRelationTypeCommand command) {
        RelationType type = relationTypeRepository.findById(new RelationType.RelationTypeId(id))
                .orElseThrow(() -> new RelationTypeNotFoundException(id));

        if (command.getName() != null) {
            type.setName(command.getName());
        }
        if (command.getInverseName() != null) {
            type.setInverseName(command.getInverseName());
        }
        if (command.getAttributesSchema() != null) {
            type.setAttributesSchema(new LinkedHashMap<>(command.getAttributesSchema()));
        }

        return RelationMapper.toDto(relationTypeRepository.saveAndFlush(type));
    }

    @Transactional
    public void deleteRelationType(Long id) {
        RelationType.RelationTypeId typeId = new RelationType.RelationTypeId(id);
        if (!relationTypeRepository.existsById(typeId)) {
            throw new RelationTypeNotFoundException(id);
        }
        long uses = relationRepository.countByRelationType_Id(typeId);
        if (uses > 0) {
            throw new ResourceInUseException("Relation type " + id + " is used by " + uses + " relations");
        }
        relationTypeRepository.deleteById(typeId);
    }

    private Collection collection(Long id) {
        return collectionRepository.findById(new Collection.CollectionId(id))
                .orElseThrow(() -> new CollectionNotFoundException(id));
    }
}
