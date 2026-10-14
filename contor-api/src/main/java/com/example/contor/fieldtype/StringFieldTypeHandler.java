package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;

/** Base for field types stored as a single {@code text} column. */
abstract class StringFieldTypeHandler extends SingleColumnFieldTypeHandler<String> {

    @Override
    protected DataType<String> dataType() {
        return SQLDataType.CLOB;
    }

    @Override
    protected String toDbValue(CollectionField field, Object value) {
        return (String) value;
    }

    @Override
    protected String parse(String raw) {
        return raw;
    }

    protected static String requireString(Object value, int maxLength) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("must be a string");
        }
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("must be at most " + maxLength + " characters");
        }
        return text;
    }
}
