package com.example.contor.service;

import com.example.contor.api.model.LabelDto;
import com.example.contor.api.model.NewLabelCommand;
import com.example.contor.api.model.UpdateLabelCommand;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.HexColor;
import com.example.contor.domain.Label;
import com.example.contor.domain.Slug;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.LabelNotFoundException;
import com.example.contor.persistence.LabelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    private LabelRepository labelRepository;

    @InjectMocks
    private LabelService labelService;

    private Label label() {
        return Label.builder().id(new Label.LabelId(1L)).slug(new Slug("invoices")).name("Invoices")
                .color(new HexColor("#ff0000")).createdBy(CreatedBy.USER).build();
    }

    @Test
    void createLabel() {
        when(labelRepository.saveAndFlush(any(Label.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LabelDto result = labelService.createLabel(new NewLabelCommand("invoices", "Invoices", "#ff0000").description("Bills"));

        assertEquals("invoices", result.getSlug());
        assertEquals("#ff0000", result.getColor());
        assertEquals("Bills", result.getDescription());
        verify(labelRepository).saveAndFlush(argThat(l -> l.getCreatedBy() == CreatedBy.USER));
    }

    @Test
    void createLabel_DuplicateSlugRejected() {
        when(labelRepository.existsBySlug(new Slug("invoices"))).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> labelService.createLabel(new NewLabelCommand("invoices", "Invoices", "#ff0000")));
        verify(labelRepository, never()).saveAndFlush(any());
    }

    @Test
    void createLabel_InvalidColorRejected() {
        assertThrows(IllegalArgumentException.class, () -> labelService.createLabel(new NewLabelCommand("invoices", "Invoices", "red")));
        verify(labelRepository, never()).saveAndFlush(any());
    }

    @Test
    void createLabel_InvalidSlugRejected() {
        assertThrows(IllegalArgumentException.class, () -> labelService.createLabel(new NewLabelCommand("Invoices!", "Invoices", "#ff0000")));
    }

    @Test
    void updateLabel_OnlyChangesGivenFields() {
        when(labelRepository.findById(new Label.LabelId(1L))).thenReturn(Optional.of(label()));
        when(labelRepository.saveAndFlush(any(Label.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LabelDto result = labelService.updateLabel(1L, new UpdateLabelCommand().color("#00ff00"));

        assertEquals("Invoices", result.getName());
        assertEquals("#00ff00", result.getColor());
    }

    @Test
    void findAll() {
        when(labelRepository.findAllByOrderByNameAsc()).thenReturn(List.of(label()));

        assertEquals(1, labelService.findAll().size());
    }

    @Test
    void findById_NotFound() {
        when(labelRepository.findById(new Label.LabelId(1L))).thenReturn(Optional.empty());

        assertThrows(LabelNotFoundException.class, () -> labelService.findById(1L));
    }

    @Test
    void deleteLabel() {
        when(labelRepository.existsById(new Label.LabelId(1L))).thenReturn(true);

        labelService.deleteLabel(1L);

        verify(labelRepository).deleteById(new Label.LabelId(1L));
    }

    @Test
    void deleteLabel_NotFound() {
        when(labelRepository.existsById(new Label.LabelId(1L))).thenReturn(false);

        assertThrows(LabelNotFoundException.class, () -> labelService.deleteLabel(1L));
        verify(labelRepository, never()).deleteById(any());
    }
}
