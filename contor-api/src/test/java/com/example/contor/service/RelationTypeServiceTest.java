package com.example.contor.service;

import com.example.contor.domain.Collection;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.Slug;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationTypeServiceTest {

    @Mock
    private RelationTypeRepository relationTypeRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @InjectMocks
    private RelationTypeService relationTypeService;

    @Test
    void createSystemRelationType() {
        var people = Collection.builder().id(new Collection.CollectionId(1L)).build();
        var organizations = Collection.builder().id(new Collection.CollectionId(2L)).build();
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
}
