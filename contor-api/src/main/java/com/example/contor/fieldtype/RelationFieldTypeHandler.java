package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Reference to one record of {@code options.targetCollectionId}: a {@code bigint} column with a foreign key to the
 * target table (on delete set null) and an index. Existence of the referenced record is checked by the record service.
 */
@Component
public class RelationFieldTypeHandler extends SingleColumnFieldTypeHandler<Long> {

    private static final String MESSAGE = "must be a record id";
    private static final String OPTIONS_MESSAGE = "RELATION options need 'targetCollectionId'";

    @Override
    public FieldType fieldType() {
        return FieldType.RELATION;
    }

    public static Long targetCollectionId(CollectionField field) {
        return FieldOptions.positiveLong(field.getOptions(), "targetCollectionId", OPTIONS_MESSAGE)
                .orElseThrow(() -> new IllegalArgumentException(OPTIONS_MESSAGE));
    }

    @Override
    protected DataType<Long> dataType() {
        return SQLDataType.BIGINT;
    }

    @Override
    public void validateOptions(Map<String, Object> options) {
        FieldOptions.positiveLong(options, "targetCollectionId", OPTIONS_MESSAGE)
                .orElseThrow(() -> new IllegalArgumentException(OPTIONS_MESSAGE));
    }

    @Override
    public List<ColumnSpec> columns(CollectionField field) {
        return List.of(new ColumnSpec(field.getColumnName(), dataType(), targetCollectionId(field)));
    }

    @Override
    public void validate(CollectionField field, Object value) {
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() <= 0) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }

    @Override
    protected Long toDbValue(CollectionField field, Object value) {
        return ((Number) value).longValue();
    }

    @Override
    protected Long parse(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}
