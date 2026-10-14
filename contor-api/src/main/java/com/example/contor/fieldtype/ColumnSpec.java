package com.example.contor.fieldtype;

import org.jooq.DataType;

/**
 * A column of a generated collection table.
 *
 * @param referencedCollectionId set for RELATION columns: the collection whose table the column references (FK + index)
 */
public record ColumnSpec(String name, DataType<?> dataType, Long referencedCollectionId) {

    public static ColumnSpec of(String name, DataType<?> dataType) {
        return new ColumnSpec(name, dataType, null);
    }
}
