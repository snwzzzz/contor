package com.example.contor.persistence.dynamic;

import com.example.contor.DynamicTableTest;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Aggregate;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Bucket;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.GroupBy;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Measure;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.jooq.impl.DSL.name;

@DynamicTableTest
@Import({CollectionTableDdl.class, DynamicRecordRepository.class, DynamicAggregateRepository.class})
class DynamicAggregateRepositoryTest {

    private static final String ORDERS = "c_orders";
    private static final String CUSTOMERS = "c_customers";
    private static final Field<String> CUSTOMER = DSL.field(name("customer"), SQLDataType.CLOB);
    private static final Field<Long> CUSTOMER_REF = DSL.field(name("customer_ref"), SQLDataType.BIGINT);
    private static final Field<BigDecimal> AMOUNT = DSL.field(name("total_amount"), SQLDataType.NUMERIC(19, 4));
    private static final Field<String> CURRENCY = DSL.field(name("total_currency"), SQLDataType.CHAR(3));

    @Autowired
    private CollectionTableDdl ddl;
    @Autowired
    private DynamicRecordRepository records;
    @Autowired
    private DynamicAggregateRepository repository;
    @Autowired
    private DSLContext dsl;

    private long kessler;
    private long moreau;
    private long first;
    private long second;
    private long third;
    private long fourth;

    @BeforeEach
    void setUp() {
        ddl.createTable(CUSTOMERS);
        ddl.createTable(ORDERS);
        ddl.addColumn(ORDERS, "customer", SQLDataType.CLOB);
        ddl.addColumn(ORDERS, "customer_ref", SQLDataType.BIGINT);
        ddl.addColumn(ORDERS, "total_amount", SQLDataType.NUMERIC(19, 4));
        ddl.addColumn(ORDERS, "total_currency", SQLDataType.CHAR(3));
        kessler = insert(CUSTOMERS, Map.of(DynamicRecordRepository.TITLE, "Kessler"));
        moreau = insert(CUSTOMERS, Map.of(DynamicRecordRepository.TITLE, "Moreau"));
        first = order("Kessler GmbH", kessler, "100", "EUR");
        second = order(" kessler gmbh ", kessler, "50.5", "EUR");
        third = order("Moreau SARL", moreau, "80", "CHF");
        fourth = order("Moreau SARL", null, null, null);
    }

    @Test
    void groups_a_text_field_ignoring_case_and_spaces_and_sums_per_currency() {
        List<Bucket> buckets = repository.aggregate(ORDERS, DSL.noCondition(),
                new Aggregate(new GroupBy.ByColumn(CUSTOMER, true), Measure.SUM, AMOUNT, CURRENCY));

        assertThat(buckets).extracting(Bucket::label, Bucket::currency, Bucket::count, Bucket::withValue)
                .containsExactlyInAnyOrder(
                        tuple("Kessler GmbH", "EUR", 2L, 2L),
                        tuple("Moreau SARL", "CHF", 1L, 1L),
                        tuple("Moreau SARL", null, 1L, 0L));
        Bucket kesslerOrders = buckets.stream().filter(bucket -> "kessler gmbh".equals(bucket.key())).findFirst().orElseThrow();
        assertThat(kesslerOrders.key()).isEqualTo("kessler gmbh");
        assertThat(kesslerOrders.value()).isEqualByComparingTo("150.5");
        assertThat(kesslerOrders.recordIds()).containsExactly(first, second);
    }

    @Test
    void counts_per_value_of_a_reference_column() {
        List<Bucket> buckets = repository.aggregate(ORDERS, DSL.noCondition(),
                new Aggregate(new GroupBy.ByColumn(CUSTOMER_REF, false), Measure.COUNT, null, null));

        Map<Object, Long> counts = new LinkedHashMap<>();
        buckets.forEach(bucket -> counts.put(bucket.key(), bucket.count()));
        assertThat(counts).containsEntry(kessler, 2L).containsEntry(moreau, 1L).containsEntry(null, 1L);
        assertThat(buckets).allSatisfy(bucket -> assertThat(bucket.value()).isNull());
    }

    @Test
    void aggregates_the_matching_records_without_grouping() {
        List<Bucket> buckets = repository.aggregate(ORDERS, CURRENCY.eq("EUR"),
                new Aggregate(new GroupBy.None(), Measure.MAX, AMOUNT, CURRENCY));

        assertThat(buckets).singleElement().satisfies(bucket -> {
            assertThat(bucket.key()).isNull();
            assertThat(bucket.count()).isEqualTo(2);
            assertThat(bucket.value()).isEqualByComparingTo("100");
            assertThat(bucket.recordIds()).containsExactly(first, second);
        });
    }

    @Test
    void averages_a_number_without_a_currency() {
        List<Bucket> buckets = repository.aggregate(ORDERS, AMOUNT.isNotNull(),
                new Aggregate(new GroupBy.None(), Measure.AVG, AMOUNT, null));

        assertThat(buckets).singleElement().satisfies(bucket -> {
            assertThat(bucket.currency()).isNull();
            assertThat(bucket.value()).isCloseTo(new BigDecimal("76.8333"), within(new BigDecimal("0.0001")));
        });
    }

    @Test
    void groups_by_the_records_linked_through_a_relation_type() {
        long customers = collection("customers", CUSTOMERS);
        long orders = collection("orders", ORDERS);
        long placedBy = relationType("placed_by", orders, customers);
        relation(placedBy, orders, first, customers, kessler);
        relation(placedBy, orders, third, customers, moreau);
        relation(placedBy, orders, fourth, customers, moreau);

        List<Bucket> buckets = repository.aggregate(ORDERS, DSL.noCondition(),
                new Aggregate(new GroupBy.ByLink(placedBy, orders, customers, true), Measure.COUNT, null, null));

        Map<Object, List<Long>> ids = new LinkedHashMap<>();
        buckets.forEach(bucket -> ids.put(bucket.key(), bucket.recordIds()));
        assertThat(ids).containsEntry(kessler, List.of(first)).containsEntry(moreau, List.of(third, fourth))
                .containsEntry(null, List.of(second));
    }

    @Test
    void groups_incoming_links_from_the_other_end() {
        long customers = collection("customers", CUSTOMERS);
        long orders = collection("orders", ORDERS);
        long places = relationType("places", customers, orders);
        relation(places, customers, kessler, orders, first);
        relation(places, customers, kessler, orders, second);

        List<Bucket> buckets = repository.aggregate(ORDERS, DSL.noCondition(),
                new Aggregate(new GroupBy.ByLink(places, orders, customers, false), Measure.SUM, AMOUNT, CURRENCY));

        assertThat(buckets).filteredOn(bucket -> Long.valueOf(kessler).equals(bucket.key())).singleElement()
                .satisfies(bucket -> assertThat(bucket.value()).isEqualByComparingTo("150.5"));
    }

    @Test
    void applies_the_filter_before_the_join() {
        long customers = collection("customers", CUSTOMERS);
        long orders = collection("orders", ORDERS);
        long placedBy = relationType("placed_by", orders, customers);
        relation(placedBy, orders, first, customers, kessler);

        List<Bucket> buckets = repository.aggregate(ORDERS, DynamicRecordRepository.ID.eq(first),
                new Aggregate(new GroupBy.ByLink(placedBy, orders, customers, true), Measure.COUNT, null, null));

        assertThat(buckets).singleElement().satisfies(bucket -> assertThat(bucket.recordIds()).containsExactly(first));
    }

    @Test
    void all_records_of_an_empty_selection_are_one_empty_bucket() {
        List<Bucket> buckets = repository.aggregate(ORDERS, DSL.falseCondition(), new Aggregate(new GroupBy.None(), Measure.SUM, AMOUNT, null));

        assertThat(buckets).singleElement().satisfies(bucket -> {
            assertThat(bucket.count()).isZero();
            assertThat(bucket.value()).isNull();
            assertThat(bucket.recordIds()).isEmpty();
        });
    }

    private long order(String customer, Long customerRef, String amount, String currency) {
        Map<Field<?>, Object> values = new LinkedHashMap<>();
        values.put(DynamicRecordRepository.TITLE, "Order of " + customer);
        values.put(CUSTOMER, customer);
        values.put(CUSTOMER_REF, customerRef);
        values.put(AMOUNT, amount == null ? null : new BigDecimal(amount));
        values.put(CURRENCY, currency);
        return insert(ORDERS, values);
    }

    private long insert(String table, Map<Field<?>, Object> values) {
        Map<Field<?>, Object> all = new LinkedHashMap<>(values);
        all.put(DynamicRecordRepository.CREATED_BY, "USER");
        return records.insert(table, all);
    }

    private long collection(String slug, String table) {
        return dsl.resultQuery("insert into collection (id, slug, name, table_name, created_by, created_at) "
                + "values (nextval('collection_seq'), ?, ?, ?, 'USER', now()) returning id", slug, slug, table).fetchSingle().get(0, Long.class);
    }

    private long relationType(String slug, long from, long to) {
        return dsl.resultQuery("insert into relation_type (id, slug, name, from_collection_id, to_collection_id, created_by, created_at) "
                + "values (nextval('relation_type_seq'), ?, ?, ?, ?, 'USER', now()) returning id", slug, slug, from, to).fetchSingle().get(0, Long.class);
    }

    private void relation(long type, long fromCollection, long fromRecord, long toCollection, long toRecord) {
        dsl.execute("insert into relation (id, relation_type_id, from_collection_id, from_record_id, to_collection_id, to_record_id, "
                + "created_by, created_at) values (nextval('relation_seq'), ?, ?, ?, ?, ?, 'USER', now())",
                type, fromCollection, fromRecord, toCollection, toRecord);
    }
}
