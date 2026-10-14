package com.example.contor.service;

import com.example.contor.api.model.NewSavedQueryCommand;
import com.example.contor.api.model.SavedQueryDto;
import com.example.contor.api.model.UpdateSavedQueryCommand;
import com.example.contor.domain.SavedQuery;
import com.example.contor.exception.SavedQueryNotFoundException;
import com.example.contor.mapper.SavedQueryMapper;
import com.example.contor.persistence.SavedQueryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

/** Saved queries are stored only in phase 1; the DSL format and its execution come later. */
@RequiredArgsConstructor
@Service
public class SavedQueryService {

    private final SavedQueryRepository savedQueryRepository;

    @Transactional
    public SavedQueryDto createSavedQuery(NewSavedQueryCommand command) {
        requireName(command.getName());

        SavedQuery query = SavedQuery.builder()
                .name(command.getName().trim())
                .description(command.getDescription())
                .dsl(new LinkedHashMap<>(command.getDsl()))
                .pinned(Boolean.TRUE.equals(command.getPinned()))
                .build();

        return SavedQueryMapper.toDto(savedQueryRepository.saveAndFlush(query));
    }

    @Transactional
    public List<SavedQueryDto> findAll() {
        return savedQueryRepository.findAllByOrderByPinnedDescNameAsc().stream()
                .map(SavedQueryMapper::toDto)
                .toList();
    }

    @Transactional
    public SavedQueryDto findById(Long id) {
        return savedQueryRepository.findById(new SavedQuery.SavedQueryId(id))
                .map(SavedQueryMapper::toDto)
                .orElseThrow(() -> new SavedQueryNotFoundException(id));
    }

    @Transactional
    public SavedQueryDto updateSavedQuery(Long id, UpdateSavedQueryCommand command) {
        SavedQuery query = savedQueryRepository.findById(new SavedQuery.SavedQueryId(id))
                .orElseThrow(() -> new SavedQueryNotFoundException(id));

        if (command.getName() != null) {
            requireName(command.getName());
            query.setName(command.getName().trim());
        }
        if (command.getDescription() != null) {
            query.setDescription(command.getDescription());
        }
        if (command.getDsl() != null) {
            query.setDsl(new LinkedHashMap<>(command.getDsl()));
        }
        if (command.getPinned() != null) {
            query.setPinned(command.getPinned());
        }

        return SavedQueryMapper.toDto(savedQueryRepository.saveAndFlush(query));
    }

    @Transactional
    public void deleteSavedQuery(Long id) {
        if (!savedQueryRepository.existsById(new SavedQuery.SavedQueryId(id))) {
            throw new SavedQueryNotFoundException(id);
        }
        savedQueryRepository.deleteById(new SavedQuery.SavedQueryId(id));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
