package com.example.contor.persistence.dynamic;

import com.example.contor.DynamicTableTest;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.name;

@DynamicTableTest
@Import({CollectionTableDdl.class, DynamicRecordRepository.class})
class DynamicRecordRepositoryTest {

    private static final String TABLE = "c_deliveries";
    private static final Field<String> CARRIER = DSL.field(name("carrier"), SQLDataType.CLOB);
    private static final Field<LocalDate> ETA = DSL.field(name("eta"), SQLDataType.LOCALDATE);
    private static final Field<BigDecimal> WEIGHT = DSL.field(name("weight"), SQLDataType.NUMERIC);
    private static final List<Field<?>> COLUMNS = List.of(CARRIER, ETA, WEIGHT);

    @Autowired
    private CollectionTableDdl ddl;

    @Autowired
    private DynamicRecordRepository repository;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void createTable() {
        ddl.createTable(TABLE);
        ddl.addColumn(TABLE, "carrier", SQLDataType.CLOB);
        ddl.addColumn(TABLE, "eta", SQLDataType.LOCALDATE);
        ddl.addColumn(TABLE, "weight", SQLDataType.NUMERIC);
    }

    private long insert(String carrier, LocalDate eta, String weight) {
        Map<Field<?>, Object> values = new LinkedHashMap<>();
        values.put(DynamicRecordRepository.TITLE, carrier + " parcel");
        values.put(DynamicRecordRepository.CREATED_BY, "USER");
        values.put(DynamicRecordRepository.EXTRA, JSONB.valueOf("{\"note\":\"fragile\"}"));
        values.put(CARRIER, carrier);
        values.put(ETA, eta);
        values.put(WEIGHT, weight == null ? null : new BigDecimal(weight));
        return repository.insert(TABLE, values);
    }

    @Test
    void inserts_and_finds_typed_values() {
        long id = insert("DHL", LocalDate.of(2026, 3, 1), "2.5");

        var record = repository.findById(TABLE, COLUMNS, id).orElseThrow();

        assertThat(record.get(DynamicRecordRepository.ID)).isEqualTo(id);
        assertThat(record.get(DynamicRecordRepository.TITLE)).isEqualTo("DHL parcel");
        assertThat(record.get(CARRIER)).isEqualTo("DHL");
        assertThat(record.get(ETA)).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(record.get(WEIGHT)).isEqualByComparingTo("2.5");
        assertThat(record.get(DynamicRecordRepository.EXTRA).data()).contains("fragile");
        assertThat(record.get(DynamicRecordRepository.CREATED_AT)).isNotNull();
        assertThat(record.get(DynamicRecordRepository.LAST_MODIFIED)).isNull();
        assertThat(repository.exists(TABLE, id)).isTrue();
        assertThat(repository.findById(TABLE, COLUMNS, id + 1000)).isEmpty();
    }

    @Test
    void stores_null_for_every_column_type() {
        Map<Field<?>, Object> values = new LinkedHashMap<>();
        values.put(DynamicRecordRepository.CREATED_BY, "USER");
        values.put(ETA, null);
        values.put(WEIGHT, null);

        long id = repository.insert(TABLE, values);

        assertThat(repository.findById(TABLE, COLUMNS, id).orElseThrow().get(ETA)).isNull();
    }

    @Test
    void updates_given_columns_and_stamps_last_modified() {
        long id = insert("DHL", LocalDate.of(2026, 3, 1), "2.5");

        repository.update(TABLE, id, Map.of(CARRIER, "UPS"));

        var record = repository.findById(TABLE, COLUMNS, id).orElseThrow();
        assertThat(record.get(CARRIER)).isEqualTo("UPS");
        assertThat(record.get(ETA)).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(record.get(DynamicRecordRepository.LAST_MODIFIED)).isNotNull();
    }

    @Test
    void deletes() {
        long id = insert("DHL", null, null);

        assertThat(repository.delete(TABLE, id)).isTrue();
        assertThat(repository.delete(TABLE, id)).isFalse();
        assertThat(repository.exists(TABLE, id)).isFalse();
    }

    @Test
    void finds_a_filtered_sorted_page_and_counts() {
        insert("DHL", LocalDate.of(2026, 3, 3), "1");
        insert("UPS", LocalDate.of(2026, 3, 1), "2");
        insert("DHL", LocalDate.of(2026, 3, 2), "3");
        insert("DHL", LocalDate.of(2026, 3, 4), "4");

        var condition = CARRIER.eq("DHL");
        var page = repository.find(TABLE, COLUMNS, condition, ETA.asc(), 1, 2);

        assertThat(page).extracting(r -> r.get(ETA)).containsExactly(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 4));
        assertThat(repository.count(TABLE, condition)).isEqualTo(3);
        assertThat(repository.count(TABLE, DSL.noCondition())).isEqualTo(4);
    }

    @Test
    void knows_whether_another_record_has_a_value() {
        long dhl = insert("DHL", null, null);
        insert("UPS", null, null);

        assertThat(repository.existsWithValue(TABLE, CARRIER, "UPS", dhl)).isTrue();
        assertThat(repository.existsWithValue(TABLE, CARRIER, "DHL", dhl)).isFalse();
        assertThat(repository.existsWithValue(TABLE, CARRIER, "DHL", null)).isTrue();
    }
}
