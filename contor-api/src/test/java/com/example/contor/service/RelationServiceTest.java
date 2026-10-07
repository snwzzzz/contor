package com.example.contor.service;

import com.example.contor.api.model.NewRelationCommand;
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.RelationOrigin;
import com.example.contor.api.model.UpdateRelationCommand;
import com.example.contor.domain.Collection;
import com.example.contor.domain.Relation;
import com.example.contor.domain.RelationCreatedBy;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.Slug;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.exception.RelationNotFoundException;
import com.example.contor.exception.RelationTypeNotFoundException;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationRepository;
import com.example.contor.persistence.RelationTypeRepository;
import com.example.contor.persistence.dynamic.DynamicRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationServiceTest {

    @Mock
    private RelationRepository relationRepository;

    @Mock
    private RelationTypeRepository relationTypeRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private DynamicRecordRepository dynamicRecordRepository;

    @InjectMocks
    private RelationService relationService;

    private Collection people;
    private Collection organizations;
    private RelationType worksAt;
    private RelationType knows;

    @BeforeEach
    void setup() {
        people = Collection.builder().id(new Collection.CollectionId(1L)).slug(new Slug("people")).tableName("c_people").build();
        organizations = Collection.builder().id(new Collection.CollectionId(2L)).slug(new Slug("organizations")).tableName("c_organizations").build();
        worksAt = RelationType.builder().id(new RelationType.RelationTypeId(3L)).slug(new Slug("works_at")).name("works at")
                .fromCollection(people).toCollection(organizations).directed(true).build();
        knows = RelationType.builder().id(new RelationType.RelationTypeId(4L)).slug(new Slug("knows")).name("knows")
                .fromCollection(people).toCollection(people).directed(false).build();
        lenient().when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(people));
        lenient().when(collectionRepository.findById(new Collection.CollectionId(2L))).thenReturn(Optional.of(organizations));
        lenient().when(relationTypeRepository.findById(new RelationType.RelationTypeId(3L))).thenReturn(Optional.of(worksAt));
        lenient().when(relationTypeRepository.findById(new RelationType.RelationTypeId(4L))).thenReturn(Optional.of(knows));
        lenient().when(dynamicRecordRepository.exists(any(), anyLong())).thenReturn(true);
        lenient().when(relationRepository.saveAndFlush(any(Relation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createRelation() {
        RelationDto result = relationService.createRelation(new NewRelationCommand(3L, 1L, 10L, 2L, 20L).attributes(Map.of("since", "2024")));

        assertEquals(3L, result.getRelationTypeId());
        assertEquals(10L, result.getFromRecordId());
        assertEquals(20L, result.getToRecordId());
        assertEquals("2024", result.getAttributes().get("since"));
        assertEquals(RelationOrigin.USER, result.getCreatedBy());
    }

    @Test
    void createRelation_WrongCollectionsRejected() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> relationService.createRelation(new NewRelationCommand(3L, 2L, 20L, 1L, 10L)));

        assertEquals("Relation type 'works_at' connects 'people' to 'organizations'", exception.getMessage());
        verify(relationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRelation_SelfRelationRejected() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> relationService.createRelation(new NewRelationCommand(4L, 1L, 10L, 1L, 10L)));

        assertEquals("A record cannot be related to itself", exception.getMessage());
    }

    @Test
    void createRelation_MissingRecordRejected() {
        when(dynamicRecordRepository.exists("c_organizations", 20L)).thenReturn(false);

        var exception = assertThrows(RecordNotFoundException.class,
                () -> relationService.createRelation(new NewRelationCommand(3L, 1L, 10L, 2L, 20L)));
        assertEquals("Record 20 not found in collection 2", exception.getMessage());
    }

    @Test
    void createRelation_DuplicateRejected() {
        when(relationRepository.existsByRelationType_IdAndFromCollection_IdAndFromRecordIdAndToCollection_IdAndToRecordId(
                worksAt.getId(), people.getId(), 10L, organizations.getId(), 20L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> relationService.createRelation(new NewRelationCommand(3L, 1L, 10L, 2L, 20L)));
    }

    @Test
    void createRelation_UndirectedDuplicateInOtherDirectionRejected() {
        lenient().when(relationRepository.existsByRelationType_IdAndFromCollection_IdAndFromRecordIdAndToCollection_IdAndToRecordId(
                knows.getId(), people.getId(), 11L, people.getId(), 10L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> relationService.createRelation(new NewRelationCommand(4L, 1L, 10L, 1L, 11L)));
    }

    @Test
    void createRelation_InvalidValidityRejected() {
        var exception = assertThrows(IllegalArgumentException.class, () -> relationService.createRelation(new NewRelationCommand(3L, 1L, 10L, 2L, 20L)
                .validFrom(OffsetDateTime.parse("2026-01-02T00:00:00Z")).validTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"))));

        assertEquals("validTo must not be before validFrom", exception.getMessage());
    }

    @Test
    void createRelation_UnknownTypeRejected() {
        when(relationTypeRepository.findById(new RelationType.RelationTypeId(9L))).thenReturn(Optional.empty());

        assertThrows(RelationTypeNotFoundException.class, () -> relationService.createRelation(new NewRelationCommand(9L, 1L, 10L, 2L, 20L)));
    }

    @Test
    void listRelations_BothDirections() {
        var outgoing = Relation.builder().id(new Relation.RelationId(2L)).relationType(worksAt).fromCollection(people).fromRecordId(10L)
                .toCollection(organizations).toRecordId(20L).createdBy(RelationCreatedBy.USER).build();
        var incoming = Relation.builder().id(new Relation.RelationId(1L)).relationType(knows).fromCollection(people).fromRecordId(11L)
                .toCollection(people).toRecordId(10L).createdBy(RelationCreatedBy.USER).build();
        when(relationRepository.findByFromCollection_IdAndFromRecordId(people.getId(), 10L)).thenReturn(List.of(outgoing));
        when(relationRepository.findByToCollection_IdAndToRecordId(people.getId(), 10L)).thenReturn(List.of(incoming));

        assertEquals(List.of(1L, 2L), relationService.listRelations(1L, 10L).stream().map(RelationDto::getId).toList());
    }

    @Test
    void updateRelation_OnlyChangesGivenFields() {
        var relation = Relation.builder().id(new Relation.RelationId(2L)).relationType(worksAt).fromCollection(people).fromRecordId(10L)
                .toCollection(organizations).toRecordId(20L).attributes(Map.of("since", "2024")).createdBy(RelationCreatedBy.USER).build();
        when(relationRepository.findById(new Relation.RelationId(2L))).thenReturn(Optional.of(relation));

        RelationDto result = relationService.updateRelation(2L, new UpdateRelationCommand().validTo(OffsetDateTime.parse("2026-01-01T00:00:00Z")));

        assertEquals("2024", result.getAttributes().get("since"));
        assertNotNull(result.getValidTo());
    }

    @Test
    void deleteRelation_NotFound() {
        when(relationRepository.existsById(new Relation.RelationId(2L))).thenReturn(false);

        assertThrows(RelationNotFoundException.class, () -> relationService.deleteRelation(2L));
    }
}
