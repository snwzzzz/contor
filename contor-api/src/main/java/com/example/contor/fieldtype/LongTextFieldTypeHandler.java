package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

/** Multi-line text, up to 100,000 characters. */
@Component
public class LongTextFieldTypeHandler extends StringFieldTypeHandler {

    private static final int MAX_LENGTH = 100_000;

    @Override
    public FieldType fieldType() {
        return FieldType.LONG_TEXT;
    }

    @Override
    public void validate(CollectionField field, Object value) {
        requireString(value, MAX_LENGTH);
    }
}
