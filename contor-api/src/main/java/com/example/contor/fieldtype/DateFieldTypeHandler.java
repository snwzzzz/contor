package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Calendar date {@code YYYY-MM-DD} ({@code date}). */
@Component
public class DateFieldTypeHandler extends SingleColumnFieldTypeHandler<LocalDate> {

    private static final String MESSAGE = "must be a date (YYYY-MM-DD)";

    @Override
    public FieldType fieldType() {
        return FieldType.DATE;
    }

    @Override
    protected DataType<LocalDate> dataType() {
        return SQLDataType.LOCALDATE;
    }

    @Override
    public void validate(CollectionField field, Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(MESSAGE);
        }
        parse(text);
    }

    @Override
    protected LocalDate toDbValue(CollectionField field, Object value) {
        return parse((String) value);
    }

    @Override
    protected Object toApiValue(LocalDate dbValue) {
        return dbValue.toString();
    }

    @Override
    protected LocalDate parse(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}
