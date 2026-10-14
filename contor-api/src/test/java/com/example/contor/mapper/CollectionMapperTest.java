package com.example.contor.mapper;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.Creator;
import com.example.contor.api.model.DisplayConfigDto;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.DisplayConfig;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class CollectionMapperTest {

    private Collection collection() {
        var collection = Collection.builder().id(new Collection.CollectionId(3L)).slug(new Slug("deliveries")).name("Deliveries")
                .description("Parcels").icon("truck").tableName("c_deliveries").system(false)
                .matchKeys(List.of("tracking")).displayConfig(new DisplayConfig("kanban", "state", null, "tracking"))
                .schemaVersion(4).createdBy(CreatedBy.AI).fields(new ArrayList<>()).build();
        collection.getFields().add(CollectionField.builder().id(new CollectionField.CollectionFieldId(11L)).collection(collection)
                .slug(new Slug("state")).name("State").type(FieldType.STATUS).options(Map.of("states", List.of("A", "B")))
                .required(true).position(2).columnName("state").build());
        collection.getFields().add(CollectionField.builder().id(new CollectionField.CollectionFieldId(10L)).collection(collection)
                .slug(new Slug("tracking")).name("Tracking").type(FieldType.TEXT).uniqueValue(true).position(1).columnName("tracking")
                .archivedAt(Instant.parse("2026-01-01T00:00:00Z")).build());
        return collection;
    }

    @Test
    void maps_collection_with_fields_in_position_order() {
        CollectionDto dto = CollectionMapper.toDto(collection());

        assertEquals(3L, dto.getId());
        assertEquals("deliveries", dto.getSlug());
        assertEquals("Deliveries", dto.getName());
        assertEquals("truck", dto.getIcon());
        assertFalse(dto.getSystem());
        assertEquals(List.of("tracking"), dto.getMatchKeys());
        assertEquals("kanban", dto.getDisplayConfig().getDefaultView());
        assertEquals("state", dto.getDisplayConfig().getKanbanStatusField());
        assertEquals(4, dto.getSchemaVersion());
        assertEquals(Creator.AI, dto.getCreatedBy());
        assertEquals(List.of("tracking", "state"), dto.getFields().stream().map(f -> f.getSlug()).toList());
        var tracking = dto.getFields().get(0);
        assertEquals(10L, tracking.getId());
        assertEquals(CollectionFieldType.TEXT, tracking.getType());
        assertTrue(tracking.getUniqueValue());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC), tracking.getArchivedAt());
        assertEquals(List.of("A", "B"), dto.getFields().get(1).getOptions().get("states"));
        assertTrue(dto.getFields().get(1).getRequired());
    }

    @Test
    void handles_null_input() {
        assertNull(CollectionMapper.toDto((Collection) null));
        assertNull(CollectionMapper.toDto((CollectionField) null));
        assertNull(CollectionMapper.toDisplayConfig(null));
    }

    @Test
    void handles_null_id_and_value_objects() {
        CollectionDto dto = CollectionMapper.toDto(Collection.builder().build());

        assertNull(dto.getId());
        assertNull(dto.getSlug());
        assertNull(dto.getCreatedBy());
        assertTrue(dto.getFields().isEmpty());
    }

    @Test
    void maps_display_config_from_the_api() {
        var config = CollectionMapper.toDisplayConfig(new DisplayConfigDto().defaultView("calendar").calendarDateField("eta"));

        assertEquals(new DisplayConfig("calendar", null, "eta", null), config);
    }
}
