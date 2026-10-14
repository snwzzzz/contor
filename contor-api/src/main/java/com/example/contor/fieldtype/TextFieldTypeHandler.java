package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Single-line text. Option {@code maxLength} (default 1000). */
@Component
public class TextFieldTypeHandler extends StringFieldTypeHandler {

    private static final int DEFAULT_MAX_LENGTH = 1000;

    @Override
    public FieldType fieldType() {
        return FieldType.TEXT;
    }

    @Override
    public void validateOptions(Map<String, Object> options) {
        FieldOptions.positiveLong(options, "maxLength", "maxLength must be a positive integer");
    }

    @Override
    public void validate(CollectionField field, Object value) {
        int maxLength = FieldOptions.positiveLong(field.getOptions(), "maxLength", "maxLength must be a positive integer")
                .map(Long::intValue).orElse(DEFAULT_MAX_LENGTH);
        requireString(value, maxLength);
    }
}
