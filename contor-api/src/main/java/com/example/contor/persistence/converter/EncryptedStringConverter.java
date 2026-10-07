package com.example.contor.persistence.converter;

import com.example.contor.security.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

/**
 * Encrypts every {@code *_enc} column (OAuth tokens, TOTP secrets). Not auto-applied:
 * use {@code @Convert(converter = EncryptedStringConverter.class)}. Hibernate obtains it from Spring,
 * so the {@link FieldEncryptor} bean is injected.
 */
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : encryptor.decrypt(dbData);
    }
}
