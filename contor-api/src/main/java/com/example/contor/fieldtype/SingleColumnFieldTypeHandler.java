package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import org.jooq.DataType;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Base for field types stored in exactly one column named after the field. */
abstract class SingleColumnFieldTypeHandler<T> implements FieldTypeHandler {

    protected abstract DataType<T> dataType();

    /** Converts a validated API value to the database value. */
    protected abstract T toDbValue(CollectionField field, Object value);

    /** Converts a non-null database value to the API value. */
    protected Object toApiValue(T dbValue) {
        return dbValue;
    }

    protected abstract T parse(String raw);

    @Override
    public List<ColumnSpec> columns(CollectionField field) {
        return List.of(ColumnSpec.of(field.getColumnName(), dataType()));
    }

    @Override
    public Map<String, Object> toDb(CollectionField field, Object value) {
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put(field.getColumnName(), value == null ? null : toDbValue(field, value));
        return columns;
    }

    @Override
    public Object fromDb(CollectionField field, Record record) {
        T value = record.get(DSL.name(field.getColumnName()), dataType().getType());
        return value == null ? null : toApiValue(value);
    }

    @Override
    public Object parseFilterValue(CollectionField field, String raw) {
        return parse(raw);
    }
}
