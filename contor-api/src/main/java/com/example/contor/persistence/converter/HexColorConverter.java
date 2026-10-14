package com.example.contor.persistence.converter;

import com.example.contor.domain.HexColor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class HexColorConverter implements AttributeConverter<HexColor, String> {

    @Override
    public String convertToDatabaseColumn(HexColor attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public HexColor convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new HexColor(dbData);
    }
}
