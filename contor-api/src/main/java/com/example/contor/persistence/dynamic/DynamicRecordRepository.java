package com.example.contor.persistence.dynamic;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.jooq.impl.DSL.name;

/**
 * Generic CRUD on generated collection tables via jOOQ. Callers pass typed fields (column name + data type), so values
 * are bound with the right SQL type, including nulls, and reads return the declared Java types (jOOQ has no generated
 * metadata for these tables, so a plain {@code select *} would yield JDBC types such as {@code java.sql.Date}).
 * Table names must come from {@link DynamicNames}.
 */
@RequiredArgsConstructor
@Component
public class DynamicRecordRepository {

    public static final Field<Long> ID = DSL.field(name("id"), SQLDataType.BIGINT);
    public static final Field<String> TITLE = DSL.field(name("title"), SQLDataType.CLOB);
    public static final Field<String> STATUS = DSL.field(name("status"), SQLDataType.CLOB);
    public static final Field<JSONB> EXTRA = DSL.field(name("extra"), SQLDataType.JSONB);
    public static final Field<OffsetDateTime> CREATED_AT = DSL.field(name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
    public static final Field<OffsetDateTime> LAST_MODIFIED = DSL.field(name("last_modified"), SQLDataType.TIMESTAMPWITHTIMEZONE);
    public static final Field<String> CREATED_BY = DSL.field(name("created_by"), SQLDataType.CLOB);

    public static final List<Field<?>> SYSTEM_COLUMNS = List.of(ID, TITLE, STATUS, EXTRA, CREATED_AT, LAST_MODIFIED, CREATED_BY);

    private final DSLContext dsl;

    public long insert(String tableName, Map<Field<?>, Object> values) {
        return dsl.insertInto(table(tableName))
                .set(typed(values))
                .returningResult(ID)
                .fetchSingle()
                .value1();
    }

    /** System columns plus {@code columns}. */
    public Optional<Record> findById(String tableName, Collection<Field<?>> columns, long id) {
        return dsl.select(selection(columns)).from(table(tableName)).where(ID.eq(id)).fetchOptional();
    }

    public boolean exists(String tableName, long id) {
        return dsl.fetchExists(table(tableName), ID.eq(id));
    }

    /** Whether a record other than {@code excludeId} has {@code value} in {@code column}. */
    public <T> boolean existsWithValue(String tableName, Field<T> column, Object value, Long excludeId) {
        Condition condition = column.eq(DSL.val(value, column));
        if (excludeId != null) {
            condition = condition.and(ID.ne(excludeId));
        }
        return dsl.fetchExists(table(tableName), condition);
    }

    public void update(String tableName, long id, Map<Field<?>, Object> values) {
        Map<Field<?>, Object> withTimestamp = typed(values);
        withTimestamp.put(LAST_MODIFIED, DSL.currentOffsetDateTime());
        dsl.update(table(tableName)).set(withTimestamp).where(ID.eq(id)).execute();
    }

    public boolean delete(String tableName, long id) {
        return dsl.deleteFrom(table(tableName)).where(ID.eq(id)).execute() > 0;
    }

    /** System columns plus {@code columns}; ties are broken by id. */
    public List<Record> find(String tableName, Collection<Field<?>> columns, Condition condition, SortField<?> sort, int offset, int limit) {
        return dsl.select(selection(columns))
                .from(table(tableName))
                .where(condition)
                .orderBy(sort, ID.asc())
                .offset(offset)
                .limit(limit)
                .fetch()
                .map(record -> (Record) record);
    }

    public long count(String tableName, Condition condition) {
        return dsl.fetchCount(table(tableName), condition);
    }

    private static List<Field<?>> selection(Collection<Field<?>> columns) {
        List<Field<?>> selection = new ArrayList<>(SYSTEM_COLUMNS);
        selection.addAll(columns);
        return selection;
    }

    private static Table<Record> table(String tableName) {
        return DSL.table(name(tableName));
    }

    /** Binds every value with its field's data type, so e.g. a null date is sent as a typed null. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<Field<?>, Object> typed(Map<Field<?>, Object> values) {
        Map<Field<?>, Object> typed = new LinkedHashMap<>();
        values.forEach((field, value) -> typed.put(field, value instanceof Field<?> ? value : DSL.val(value, (Field) field)));
        return typed;
    }
}
