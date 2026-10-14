package com.example.contor.mapper;

import com.example.contor.api.model.SavedQueryDto;
import com.example.contor.domain.SavedQuery;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SavedQueryMapperTest {

    @Test
    void maps_saved_query_to_dto() {
        var query = SavedQuery.builder().id(new SavedQuery.SavedQueryId(2L)).name("Open").description("Open deliveries")
                .dsl(Map.of("from", "deliveries")).pinned(true).build();

        SavedQueryDto dto = SavedQueryMapper.toDto(query);

        assertEquals(2L, dto.getId());
        assertEquals("Open", dto.getName());
        assertEquals("Open deliveries", dto.getDescription());
        assertEquals("deliveries", dto.getDsl().get("from"));
        assertTrue(dto.getPinned());
    }

    @Test
    void handles_null_input_and_id() {
        assertNull(SavedQueryMapper.toDto(null));
        assertNull(SavedQueryMapper.toDto(SavedQuery.builder().name("x").build()).getId());
    }
}
