package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.Email;
import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

/** Email address, validated with the {@link Email} value object. */
@Component
public class EmailFieldTypeHandler extends StringFieldTypeHandler {

    private static final String MESSAGE = "must be an email address";

    @Override
    public FieldType fieldType() {
        return FieldType.EMAIL;
    }

    @Override
    public void validate(CollectionField field, Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(MESSAGE);
        }
        try {
            new Email(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}
