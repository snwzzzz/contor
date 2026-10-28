package com.example.contor.persistence.converter;

import com.example.contor.domain.JsonPathExpr;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JsonPathExprConverter implements AttributeConverter<JsonPathExpr, String> {

    @Override
    public String convertToDatabaseColumn(JsonPathExpr attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public JsonPathExpr convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new JsonPathExpr(dbData);
    }
}
