package com.example.contor.persistence.converter;

import com.example.contor.domain.HttpsUrl;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class HttpsUrlConverter implements AttributeConverter<HttpsUrl, String> {

    @Override
    public String convertToDatabaseColumn(HttpsUrl attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public HttpsUrl convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new HttpsUrl(dbData);
    }
}
