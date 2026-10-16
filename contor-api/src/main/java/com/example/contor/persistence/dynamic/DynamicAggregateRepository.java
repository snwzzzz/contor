package com.example.contor.persistence.dynamic;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jooq.impl.DSL.name;

/**
 * Figures over the records of a generated collection table that match a condition: grouped by a column, by the records
 * they are linked to through one relation type, or not at all; counted, and a number column summed, averaged or its
 * minimum/maximum taken — per currency when a currency column is given (amounts are never added across currencies).
 * The condition is applied to the table first, so its unqualified columns never clash with the relation table.
 * Columns are typed jOOQ fields named after the table's columns (from the field handlers' {@code ColumnSpec}s).
 */
@RequiredArgsConstructor
@Component
public class DynamicAggregateRepository {

    private static final String RECORDS = "t";
    private static final String LINKS = "r";
    private static final Table<Record> RELATION = DSL.table(name("relation"));

    /** How records are grouped. */
    public sealed interface GroupBy {

        /** One group of all matching records. */
        record None() implements GroupBy {}

        /** @param text text-like: grouped by the lower-cased, trimmed value; the label is the oldest record's spelling */
        record ByColumn(Field<?> column, boolean text) implements GroupBy {}

        /**
         * By the record of {@code otherCollectionId} each record is linked to through relations of {@code relationTypeId}
         * (a record with several links is in several groups; one without in the {@code null} group).
         *
         * @param outgoing whether the aggregated records are the relations' "from" end
         */
        record ByLink(long relationTypeId, long ownCollectionId, long otherCollectionId, boolean outgoing) implements GroupBy {}
    }

    public enum Measure { COUNT, SUM, AVG, MIN, MAX }

    /**
     * @param value    the measured number column; {@code null} for {@link Measure#COUNT}
     * @param currency the currency column of a MONEY value, else {@code null}
     */
    public record Aggregate(GroupBy groupBy, Measure measure, Field<BigDecimal> value, Field<String> currency) {}

    /**
     * One group (and currency) of the result.
     *
     * @param key       the group's value (lower-cased for text, a record id for references and links); {@code null} = none
     * @param label     a spelling of a text key as stored; {@code null} for other keys
     * @param currency  {@code null} without a currency column or for records without a value
     * @param withValue records with a value in the measured column (= {@code count} for COUNT)
     * @param value     the measure; {@code null} for COUNT or when no record has a value
     * @param recordIds the group's records, by id (empty when no record matches)
     */
    public record Bucket(Object key, String label, String currency, long count, long withValue, BigDecimal value, List<Long> recordIds) {}

    private final DSLContext dsl;

    public List<Bucket> aggregate(String tableName, Condition condition, Aggregate aggregate) {
        Table<?> records = dsl.select().from(DSL.table(name(tableName))).where(condition).asTable(RECORDS);
        Field<Long> id = DSL.field(name(RECORDS, DynamicRecordRepository.ID.getName()), SQLDataType.BIGINT);
        Field<?> key = null;
        Field<String> label = null;
        Condition join = null;
        switch (aggregate.groupBy()) {
            case GroupBy.None none -> {
            }
            case GroupBy.ByColumn byColumn when byColumn.text() -> {
                Field<String> text = DSL.field(name(RECORDS, byColumn.column().getName()), SQLDataType.CLOB);
                key = DSL.lower(DSL.trim(text));
                label = DSL.arrayGet(DSL.arrayAgg(DSL.trim(text)).orderBy(id), 1);
            }
            case GroupBy.ByColumn byColumn -> key = DSL.field(name(RECORDS, byColumn.column().getName()), byColumn.column().getDataType());
            case GroupBy.ByLink link -> {
                String own = link.outgoing() ? "from" : "to";
                String other = link.outgoing() ? "to" : "from";
                key = DSL.field(name(LINKS, other + "_record_id"), SQLDataType.BIGINT);
                join = DSL.field(name(LINKS, "relation_type_id"), SQLDataType.BIGINT).eq(link.relationTypeId())
                        .and(DSL.field(name(LINKS, own + "_collection_id"), SQLDataType.BIGINT).eq(link.ownCollectionId()))
                        .and(DSL.field(name(LINKS, own + "_record_id"), SQLDataType.BIGINT).eq(id))
                        .and(DSL.field(name(LINKS, other + "_collection_id"), SQLDataType.BIGINT).eq(link.otherCollectionId()));
            }
        }
        Field<BigDecimal> value = aggregate.value() == null ? null
                : DSL.field(name(RECORDS, aggregate.value().getName()), SQLDataType.NUMERIC);
        Field<String> currency = aggregate.currency() == null ? null
                : DSL.trim(DSL.field(name(RECORDS, aggregate.currency().getName()), SQLDataType.CLOB));

        List<Field<?>> groupFields = new ArrayList<>();
        if (key != null) {
            groupFields.add(key);
        }
        if (currency != null) {
            groupFields.add(currency);
        }
        Field<Integer> count = DSL.count();
        Field<Integer> withValue = value == null ? DSL.count() : DSL.count(value);
        Field<BigDecimal> measure = measure(aggregate.measure(), value);
        Field<Long[]> ids = DSL.arrayAgg(id).orderBy(id);
        List<Field<?>> selection = new ArrayList<>(groupFields);
        if (label != null) {
            selection.add(label);
        }
        selection.addAll(List.of(count, withValue, measure, ids));

        SelectJoinStep<Record> from = dsl.select(selection).from(records);
        if (join != null) {
            from = from.leftJoin(RELATION.as(LINKS)).on(join);
        }
        List<Bucket> buckets = new ArrayList<>();
        for (Record row : from.groupBy(groupFields).fetch()) {
            buckets.add(new Bucket(key == null ? null : row.get(key), label == null ? null : row.get(label),
                    currency == null ? null : row.get(currency), row.get(count), row.get(withValue), row.get(measure),
                    row.get(ids) == null ? List.of() : Arrays.asList(row.get(ids))));
        }
        return buckets;
    }

    private static Field<BigDecimal> measure(Measure measure, Field<BigDecimal> value) {
        if (measure == Measure.COUNT || value == null) {
            return DSL.inline(null, SQLDataType.NUMERIC);
        }
        return switch (measure) {
            case SUM -> DSL.sum(value);
            case AVG -> DSL.avg(value);
            case MIN -> DSL.min(value);
            case MAX -> DSL.max(value);
            case COUNT -> throw new IllegalStateException("unreachable");
        };
    }
}
