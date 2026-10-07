package com.example.contor.mapper;

import com.example.contor.api.model.Creator;
import com.example.contor.api.model.LabelDto;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.HexColor;
import com.example.contor.domain.Label;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class LabelMapperTest {

    @Test
    void maps_label_to_dto() {
        var label = Label.builder().id(new Label.LabelId(1L)).slug(new Slug("invoices")).name("Invoices")
                .color(new HexColor("#ff0000")).description("Bills").createdBy(CreatedBy.AI).build();

        LabelDto dto = LabelMapper.toDto(label);

        assertEquals(1L, dto.getId());
        assertEquals("invoices", dto.getSlug());
        assertEquals("Invoices", dto.getName());
        assertEquals("#ff0000", dto.getColor());
        assertEquals("Bills", dto.getDescription());
        assertEquals(Creator.AI, dto.getCreatedBy());
    }

    @Test
    void handles_null_input() {
        assertNull(LabelMapper.toDto(null));
    }

    @Test
    void handles_null_id_and_value_objects() {
        LabelDto dto = LabelMapper.toDto(Label.builder().name("x").build());

        assertNull(dto.getId());
        assertNull(dto.getSlug());
        assertNull(dto.getColor());
        assertNull(dto.getCreatedBy());
    }
}
