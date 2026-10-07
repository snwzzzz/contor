package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.springframework.stereotype.Component;

/** Reference to a stored file (storage itself comes in a later phase). */
@Component
public class FileFieldTypeHandler extends StringFieldTypeHandler {

    private static final int MAX_LENGTH = 2048;

    @Override
    public FieldType fieldType() {
        return FieldType.FILE;
    }

    @Override
    public void validate(CollectionField field, Object value) {
        requireString(value, MAX_LENGTH);
    }
}
