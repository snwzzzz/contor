package com.example.contor.mapper;

import com.example.contor.api.model.Creator;
import com.example.contor.api.model.LabelDto;
import com.example.contor.domain.Label;

public class LabelMapper {

    public static LabelDto toDto(Label label) {
        if (label == null) return null;

        return new LabelDto()
                .id(label.getId() != null ? label.getId().id() : null)
                .slug(label.getSlug() != null ? label.getSlug().value() : null)
                .name(label.getName())
                .color(label.getColor() != null ? label.getColor().value() : null)
                .description(label.getDescription())
                .createdBy(label.getCreatedBy() != null ? Creator.valueOf(label.getCreatedBy().name()) : null);
    }
}
