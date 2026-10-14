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
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationRepository;
import com.example.contor.persistence.RelationTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationTypeServiceTest {

    @Mock
    private RelationTypeRepository relationTypeRepository;

    @Mock
    private RelationRepository relationRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @InjectMocks
    private RelationTypeService relationTypeService;

    private Collection collection(long id) {
        return Collection.builder().id(new Collection.CollectionId(id)).build();
    }

    @Test
    void createSystemRelationType() {
        var people = collection(1L);
        var organizations = collection(2L);
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(people));
        when(collectionRepository.findById(new Collection.CollectionId(2L))).thenReturn(Optional.of(organizations));

        relationTypeService.createSystemRelationType("works_at", "works at", "employs", 1L, 2L, true);

        verify(relationTypeRepository).saveAndFlush(argThat((RelationType t) -> t.getSlug().equals(new Slug("works_at"))
                && t.getFromCollection() == people && t.getToCollection() == organizations
                && t.isDirected() && t.getCreatedBy() == CreatedBy.USER && "employs".equals(t.getInverseName())));
    }

    @Test
    void createSystemRelationType_CollectionNotFound() {
        when(collectionRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(CollectionNotFoundException.class,
                () -> relationTypeService.createSystemRelationType("knows", "knows", "knows", 1L, 1L, false));
        verify(relationTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRelationType() {
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(collection(1L)));
        when(relationTypeRepository.saveAndFlush(any(RelationType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RelationTypeDto result = relationTypeService.createRelationType(new NewRelationTypeCommand("ordered_by", "ordered by")
                .inverseName("ordered").fromCollectionId(1L).directed(true).attributesSchema(Map.of("role", "text")));

        assertEquals("ordered_by", result.getSlug());
        assertEquals(1L, result.getFromCollectionId());
        assertNull(result.getToCollectionId());
        assertEquals("text", result.getAttributesSchema().get("role"));
    }

    @Test
    void createRelationType_DuplicateSlugRejected() {
        when(relationTypeRepository.existsBySlug(new Slug("knows"))).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> relationTypeService.createRelationType(new NewRelationTypeCommand("knows", "knows")));
        verify(relationTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRelationType_UnknownCollectionRejected() {
        when(collectionRepository.findById(new Collection.CollectionId(9L))).thenReturn(Optional.empty());

        assertThrows(CollectionNotFoundException.class,
                () -> relationTypeService.createRelationType(new NewRelationTypeCommand("x_y", "x").toCollectionId(9L)));
    }

    @Test
    void updateRelationType_OnlyChangesGivenFields() {
        var type = RelationType.builder().id(new RelationType.RelationTypeId(3L)).slug(new Slug("works_at")).name("works at")
                .inverseName("employs").createdBy(CreatedBy.USER).build();
        when(relationTypeRepository.findById(new RelationType.RelationTypeId(3L))).thenReturn(Optional.of(type));
        when(relationTypeRepository.saveAndFlush(any(RelationType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RelationTypeDto result = relationTypeService.updateRelationType(3L, new UpdateRelationTypeCommand().inverseName("has employee"));

        assertEquals("works at", result.getName());
        assertEquals("has employee", result.getInverseName());
    }

    @Test
    void deleteRelationType_InUseRejected() {
        when(relationTypeRepository.existsById(new RelationType.RelationTypeId(3L))).thenReturn(true);
        when(relationRepository.countByRelationType_Id(new RelationType.RelationTypeId(3L))).thenReturn(2L);

        var exception = assertThrows(ResourceInUseException.class, () -> relationTypeService.deleteRelationType(3L));
        assertEquals("Relation type 3 is used by 2 relations", exception.getMessage());
        verify(relationTypeRepository, never()).deleteById(any());
    }

    @Test
    void deleteRelationType_NotFound() {
        when(relationTypeRepository.existsById(new RelationType.RelationTypeId(3L))).thenReturn(false);

        assertThrows(RelationTypeNotFoundException.class, () -> relationTypeService.deleteRelationType(3L));
    }

    @Test
    void findAll() {
        when(relationTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of(RelationType.builder().slug(new Slug("knows")).name("knows").build()));

        assertEquals(1, relationTypeService.findAll().size());
    }
}
