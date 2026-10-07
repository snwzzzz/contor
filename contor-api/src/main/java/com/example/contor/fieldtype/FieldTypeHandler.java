package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.jooq.Record;

import java.util.List;
import java.util.Map;

/**
 * Strategy for one {@link FieldType}: which columns it needs in the generated collection table, how values are
 * validated, and how they are converted between the API (JSON values) and the database.
 * <p>
 * Plug-in point: to support a new field type, add the enum constant and one {@code @Component} implementing this
 * interface. {@link FieldTypeRegistry} picks it up automatically; nothing else changes.
 * <p>
 * Validation methods throw {@link IllegalArgumentException} with a message that does not name the field
 * (callers prefix it with the field slug).
 */
public interface FieldTypeHandler {

    FieldType fieldType();

    /** Validates the type-specific options of a field definition. */
    default void validateOptions(Map<String, Object> options) {
    }

    /** Columns this field occupies in the generated table, derived from {@link CollectionField#getColumnName()}. */
    List<ColumnSpec> columns(CollectionField field);

    /** Validates a non-null API value. */
    void validate(CollectionField field, Object value);

    /** Validates a change of an existing value (e.g. STATUS transitions). Both values are API values, may be null. */
    default void validateChange(CollectionField field, Object oldValue, Object newValue) {
    }

    /** Value used when a new record does not provide one. */
    default Object defaultValue(CollectionField field) {
        return null;
    }

    /** Column name → database value for a validated API value; a {@code null} value maps every column to null. */
    Map<String, Object> toDb(CollectionField field, Object value);

    /** API value read from a record of the generated table, or {@code null}. */
    Object fromDb(CollectionField field, Record record);

    /** Parses a filter value (query string) into the database type of the first column. */
    Object parseFilterValue(CollectionField field, String raw);
}
