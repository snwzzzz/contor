package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

/** true / false ({@code boolean}). */
@Component
public class BooleanFieldTypeHandler extends SingleColumnFieldTypeHandler<Boolean> {

    private static final String MESSAGE = "must be true or false";

    @Override
    public FieldType fieldType() {
        return FieldType.BOOLEAN;
    }

    @Override
    protected DataType<Boolean> dataType() {
        return SQLDataType.BOOLEAN;
    }

    @Override
    public void validate(CollectionField field, Object value) {
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }

    @Override
    protected Boolean toDbValue(CollectionField field, Object value) {
        return (Boolean) value;
    }

    @Override
    protected Boolean parse(String raw) {
        return switch (raw) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(MESSAGE);
        };
    }
}
