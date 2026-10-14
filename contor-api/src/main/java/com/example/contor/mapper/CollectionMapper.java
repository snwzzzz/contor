package com.example.contor.mapper;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.Creator;
import com.example.contor.api.model.DisplayConfigDto;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.DisplayConfig;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class CollectionMapper {

    public static CollectionDto toDto(Collection collection) {
        if (collection == null) return null;

        List<CollectionFieldDto> fields = collection.getFields() == null ? List.of() : collection.getFields().stream()
                .sorted(Comparator.comparingInt(CollectionField::getPosition))
                .map(CollectionMapper::toDto)
                .toList();
        return new CollectionDto()
                .id(collection.getId() != null ? collection.getId().id() : null)
                .slug(collection.getSlug() != null ? collection.getSlug().value() : null)
                .name(collection.getName())
                .description(collection.getDescription())
                .icon(collection.getIcon())
                .system(collection.isSystem())
                .matchKeys(collection.getMatchKeys() != null ? new ArrayList<>(collection.getMatchKeys()) : new ArrayList<>())
                .displayConfig(toDisplayConfigDto(collection.getDisplayConfig()))
                .schemaVersion(collection.getSchemaVersion())
                .createdBy(collection.getCreatedBy() != null ? Creator.valueOf(collection.getCreatedBy().name()) : null)
                .archivedAt(toOffset(collection.getArchivedAt()))
                .fields(new ArrayList<>(fields));
    }

    public static CollectionFieldDto toDto(CollectionField field) {
        if (field == null) return null;

        return new CollectionFieldDto()
                .id(field.getId() != null ? field.getId().id() : null)
                .slug(field.getSlug() != null ? field.getSlug().value() : null)
                .name(field.getName())
                .description(field.getDescription())
                .type(field.getType() != null ? CollectionFieldType.valueOf(field.getType().name()) : null)
                .options(field.getOptions() != null ? new LinkedHashMap<>(field.getOptions()) : new LinkedHashMap<>())
                .required(field.isRequired())
                .uniqueValue(field.isUniqueValue())
                .position(field.getPosition())
                .archivedAt(toOffset(field.getArchivedAt()));
    }

    public static DisplayConfigDto toDisplayConfigDto(DisplayConfig config) {
        if (config == null) return new DisplayConfigDto();

        return new DisplayConfigDto()
                .defaultView(config.defaultView())
                .kanbanStatusField(config.kanbanStatusField())
                .calendarDateField(config.calendarDateField())
                .titleField(config.titleField());
    }

    public static DisplayConfig toDisplayConfig(DisplayConfigDto dto) {
        if (dto == null) return null;

        return new DisplayConfig(dto.getDefaultView(), dto.getKanbanStatusField(), dto.getCalendarDateField(), dto.getTitleField());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
