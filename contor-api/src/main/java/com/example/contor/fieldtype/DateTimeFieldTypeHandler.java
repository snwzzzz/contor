package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Point in time, ISO-8601 with offset ({@code timestamptz}); returned in UTC. */
@Component
public class DateTimeFieldTypeHandler extends SingleColumnFieldTypeHandler<OffsetDateTime> {

    private static final String MESSAGE = "must be an ISO-8601 date-time with offset";

    @Override
    public FieldType fieldType() {
        return FieldType.DATETIME;
    }

    @Override
    protected DataType<OffsetDateTime> dataType() {
        return SQLDataType.TIMESTAMPWITHTIMEZONE;
    }

    @Override
    public void validate(CollectionField field, Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(MESSAGE);
        }
        parse(text);
    }

    @Override
    protected OffsetDateTime toDbValue(CollectionField field, Object value) {
        return parse((String) value);
    }

    @Override
    protected Object toApiValue(OffsetDateTime dbValue) {
        return dbValue.toInstant().toString();
    }

    @Override
    protected OffsetDateTime parse(String raw) {
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}
