package com.example.contor.service;

import com.example.contor.domain.Collection;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.Slug;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationTypeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Relation types (graph edge kinds). The API for user-defined types follows with the relation endpoints. */
@RequiredArgsConstructor
@Service
public class RelationTypeService {

    private final RelationTypeRepository relationTypeRepository;
    private final CollectionRepository collectionRepository;

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

    private Collection collection(Long id) {
        return collectionRepository.findById(new Collection.CollectionId(id))
                .orElseThrow(() -> new CollectionNotFoundException(id));
    }
}
