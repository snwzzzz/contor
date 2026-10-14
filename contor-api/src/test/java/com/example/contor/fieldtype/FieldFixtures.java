package com.example.contor.fieldtype;

import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.Slug;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fields named {@code f} and jOOQ records as the generated tables return them. */
final class FieldFixtures {

    private FieldFixtures() {
    }

    static CollectionField field(FieldType type) {
        return field(type, Map.of());
    }

    static CollectionField field(FieldType type, Map<String, Object> options) {
        return CollectionField.builder().slug(new Slug("field")).name("Field").type(type)
                .options(new LinkedHashMap<>(options)).columnName("f").position(1).build();
    }

    static Record record(Map<String, Object> columns) {
        List<Field<?>> fields = columns.keySet().stream().<Field<?>>map(name -> DSL.field(DSL.name(name))).toList();
        Record record = DSL.using(SQLDialect.POSTGRES).newRecord(fields.toArray(Field<?>[]::new));
        columns.forEach((name, value) -> record.set(DSL.field(DSL.name(name)), value));
        return record;
    }

    static Record record(String column, Object value) {
        var columns = new LinkedHashMap<String, Object>();
        columns.put(column, value);
        return record(columns);
    }
}
