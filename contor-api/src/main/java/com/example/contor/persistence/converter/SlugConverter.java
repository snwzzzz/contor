package com.example.contor.persistence.converter;

import com.example.contor.domain.Slug;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SlugConverter implements AttributeConverter<Slug, String> {

    @Override
    public String convertToDatabaseColumn(Slug attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Slug convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Slug(dbData);
    }
}
