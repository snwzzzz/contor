package com.example.contor.mapper;

import com.example.contor.api.model.SavedQueryDto;
import com.example.contor.domain.SavedQuery;

import java.util.LinkedHashMap;

public class SavedQueryMapper {

    public static SavedQueryDto toDto(SavedQuery query) {
        if (query == null) return null;

        return new SavedQueryDto()
                .id(query.getId() != null ? query.getId().id() : null)
                .name(query.getName())
                .description(query.getDescription())
                .dsl(query.getDsl() != null ? new LinkedHashMap<>(query.getDsl()) : new LinkedHashMap<>())
                .pinned(query.isPinned());
    }
}
