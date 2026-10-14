package com.example.contor.service;

import com.example.contor.api.model.NewSavedQueryCommand;
import com.example.contor.api.model.SavedQueryDto;
import com.example.contor.api.model.UpdateSavedQueryCommand;
import com.example.contor.domain.SavedQuery;
import com.example.contor.exception.SavedQueryNotFoundException;
import com.example.contor.persistence.SavedQueryRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedQueryServiceTest {

    @Mock
    private SavedQueryRepository savedQueryRepository;

    @InjectMocks
    private SavedQueryService savedQueryService;

    @Test
    void createSavedQuery() {
        when(savedQueryRepository.saveAndFlush(any(SavedQuery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SavedQueryDto result = savedQueryService.createSavedQuery(new NewSavedQueryCommand("Open", Map.of("from", "deliveries")));

        assertEquals("Open", result.getName());
        assertEquals("deliveries", result.getDsl().get("from"));
        assertFalse(result.getPinned());
    }

    @Test
    void createSavedQuery_BlankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> savedQueryService.createSavedQuery(new NewSavedQueryCommand("", Map.of())));
        verify(savedQueryRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSavedQuery_OnlyChangesGivenFields() {
        var query = SavedQuery.builder().id(new SavedQuery.SavedQueryId(1L)).name("Open").dsl(Map.of("from", "deliveries")).build();
        when(savedQueryRepository.findById(new SavedQuery.SavedQueryId(1L))).thenReturn(Optional.of(query));
        when(savedQueryRepository.saveAndFlush(any(SavedQuery.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SavedQueryDto result = savedQueryService.updateSavedQuery(1L, new UpdateSavedQueryCommand().pinned(true));

        assertTrue(result.getPinned());
        assertEquals("Open", result.getName());
        assertEquals("deliveries", result.getDsl().get("from"));
    }

    @Test
    void findAll_PinnedFirst() {
        when(savedQueryRepository.findAllByOrderByPinnedDescNameAsc()).thenReturn(List.of(SavedQuery.builder().name("x").dsl(Map.of()).build()));

        assertEquals(1, savedQueryService.findAll().size());
    }

    @Test
    void findById_NotFound() {
        when(savedQueryRepository.findById(new SavedQuery.SavedQueryId(1L))).thenReturn(Optional.empty());

        assertThrows(SavedQueryNotFoundException.class, () -> savedQueryService.findById(1L));
    }

    @Test
    void deleteSavedQuery_NotFound() {
        when(savedQueryRepository.existsById(new SavedQuery.SavedQueryId(1L))).thenReturn(false);

        assertThrows(SavedQueryNotFoundException.class, () -> savedQueryService.deleteSavedQuery(1L));
    }
}
