package com.example.contor.persistence.converter;

import com.example.contor.domain.ApiPath;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApiPathConverter implements AttributeConverter<ApiPath, String> {

    @Override
    public String convertToDatabaseColumn(ApiPath attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ApiPath convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new ApiPath(dbData);
    }
}
