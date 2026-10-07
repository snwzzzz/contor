package com.example.contor.persistence.dynamic;

import com.example.contor.DynamicTableTest;
import org.jooq.DSLContext;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@DynamicTableTest
@Import(CollectionTableDdl.class)
class CollectionTableDdlTest {

    @Autowired
    private CollectionTableDdl ddl;

    @Autowired
    private DSLContext dsl;

    private Map<String, String> columnTypes(String table) {
        return dsl.resultQuery("select column_name, data_type from information_schema.columns where table_name = ?", table)
                .fetch().stream()
                .collect(Collectors.toMap(r -> r.get(0, String.class), r -> r.get(1, String.class)));
    }

    @Test
    void creates_the_table_with_system_columns_and_returns_the_executed_sql() {
        var statements = ddl.createTable("c_deliveries");

        assertThat(columnTypes("c_deliveries")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "bigint",
                "title", "text",
                "status", "text",
                "extra", "jsonb",
                "created_at", "timestamp with time zone",
                "last_modified", "timestamp with time zone",
                "created_by", "text"));
        assertThat(statements).anySatisfy(sql -> assertThat(sql).startsWithIgnoringCase("create table \"c_deliveries\""));
        assertThat(dsl.fetchValue("select is_identity from information_schema.columns where table_name = 'c_deliveries' and column_name = 'id'"))
                .isEqualTo("YES");
    }

    @Test
    void grants_select_to_the_read_only_role_only_on_the_collection_table() {
        var statements = ddl.createTable("c_deliveries");

        assertThat(dsl.fetchValue("select has_table_privilege('app_readonly', 'c_deliveries', 'SELECT')")).isEqualTo(true);
        assertThat(dsl.fetchValue("select has_table_privilege('app_readonly', 'c_deliveries', 'INSERT')")).isEqualTo(false);
        assertThat(dsl.fetchValue("select has_table_privilege('app_readonly', 'app_user', 'SELECT')")).isEqualTo(false);
        assertThat(statements).anySatisfy(sql -> assertThat(sql).containsIgnoringCase("grant select"));
    }

    @Test
    void adds_typed_columns() {
        ddl.createTable("c_deliveries");

        ddl.addColumn("c_deliveries", "eta", SQLDataType.LOCALDATE);
        ddl.addColumn("c_deliveries", "price_amount", SQLDataType.NUMERIC(19, 4));
        ddl.addColumn("c_deliveries", "price_currency", SQLDataType.CHAR(3));

        assertThat(columnTypes("c_deliveries"))
                .containsEntry("eta", "date")
                .containsEntry("price_amount", "numeric")
                .containsEntry("price_currency", "character");
        assertThat(dsl.fetchValue("select numeric_scale from information_schema.columns where table_name = 'c_deliveries' and column_name = 'price_amount'"))
                .isEqualTo(4);
    }

    @Test
    void adds_a_foreign_key_that_nulls_on_delete_plus_an_index() {
        ddl.createTable("c_organizations");
        ddl.createTable("c_people");
        ddl.addColumn("c_people", "employer", SQLDataType.BIGINT);

        var statements = ddl.addForeignKey("c_people", "employer", "c_organizations");

        Long orgId = dsl.insertInto(table(name("c_organizations"))).set(field(name("title")), "ACME")
                .returningResult(field(name("id"), Long.class)).fetchOne().value1();
        dsl.insertInto(table(name("c_people"))).set(field(name("employer")), orgId).execute();
        assertThat(dsl.fetchValue("select confdeltype from pg_constraint where conname = 'fk_c_people_employer'"))
                .hasToString("n");
        assertThat(statements).anySatisfy(sql -> assertThat(sql).containsIgnoringCase("create index"));
        assertThat(dsl.fetchValue("select count(*) from pg_indexes where tablename = 'c_people' and indexdef like '%(employer)%'"))
                .isEqualTo(1L);

        dsl.deleteFrom(table(name("c_organizations"))).execute();
        assertThat(dsl.fetchValue("select employer from c_people")).isNull();
    }

    @Test
    void adds_a_unique_index() {
        ddl.createTable("c_orders");
        ddl.addColumn("c_orders", "order_number", SQLDataType.CLOB);
        ddl.addUniqueIndex("c_orders", "order_number");

        dsl.insertInto(table(name("c_orders"))).set(field(name("order_number")), "A-1").execute();

        assertThatThrownBy(() -> dsl.insertInto(table(name("c_orders"))).set(field(name("order_number")), "A-1").execute())
                .isInstanceOf(DataAccessException.class);
    }
}
