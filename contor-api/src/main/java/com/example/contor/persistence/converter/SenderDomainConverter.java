package com.example.contor.persistence.converter;

import com.example.contor.domain.SenderDomain;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SenderDomainConverter implements AttributeConverter<SenderDomain, String> {

    @Override
    public String convertToDatabaseColumn(SenderDomain attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SenderDomain convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new SenderDomain(dbData);
    }
}
