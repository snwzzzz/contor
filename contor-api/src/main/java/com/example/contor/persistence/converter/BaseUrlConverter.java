package com.example.contor.persistence.converter;

import com.example.contor.domain.BaseUrl;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BaseUrlConverter implements AttributeConverter<BaseUrl, String> {

    @Override
    public String convertToDatabaseColumn(BaseUrl attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public BaseUrl convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new BaseUrl(dbData);
    }
}
