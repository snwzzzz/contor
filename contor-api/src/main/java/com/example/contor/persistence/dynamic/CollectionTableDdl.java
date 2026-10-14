package com.example.contor.persistence.dynamic;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.JSONB;
import org.jooq.Query;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.constraint;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.privilege;
import static org.jooq.impl.DSL.role;
import static org.jooq.impl.DSL.table;

/**
 * DDL for generated collection tables, built with the jOOQ DSL only (all identifiers quoted, never concatenated SQL).
 * Runs inside the caller's transaction (PostgreSQL DDL is transactional). Every method returns the SQL it executed,
 * for the {@code collection_schema_change} history.
 */
@RequiredArgsConstructor
@Component
public class CollectionTableDdl {

    public static final String READ_ONLY_ROLE = "app_readonly";

    private final DSLContext dsl;

    /** Creates the table with the system columns and grants SELECT to the read-only role. */
    public List<String> createTable(String tableName) {
        Query create = dsl.createTable(name(tableName))
                .column(field(name("id"), SQLDataType.BIGINT.identity(true)))
                .column(field(name("title"), SQLDataType.CLOB))
                .column(field(name("status"), SQLDataType.CLOB))
                .column(field(name("extra"), SQLDataType.JSONB.notNull().defaultValue(inline(JSONB.valueOf("{}")))))
                .column(field(name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.notNull().defaultValue(currentOffsetDateTime())))
                .column(field(name("last_modified"), SQLDataType.TIMESTAMPWITHTIMEZONE))
                .column(field(name("created_by"), SQLDataType.CLOB.notNull().defaultValue(inline("SYSTEM"))))
                .constraint(constraint(name(DynamicNames.constraintName("pk", tableName, "id"))).primaryKey(name("id")));
        Query grant = dsl.grant(privilege("select")).on(table(name(tableName))).to(role(name(READ_ONLY_ROLE)));
        return execute(create, grant);
    }

    public List<String> addColumn(String tableName, String column, DataType<?> dataType) {
        return execute(dsl.alterTable(name(tableName)).add(field(name(column), dataType)));
    }

    /** Foreign key to {@code targetTable(id)} that sets the column to null when the target record is deleted, plus an index. */
    public List<String> addForeignKey(String tableName, String column, String targetTable) {
        Query foreignKey = dsl.alterTable(name(tableName))
                .add(constraint(name(DynamicNames.constraintName("fk", tableName, column)))
                        .foreignKey(name(column))
                        .references(name(targetTable), name("id"))
                        .onDeleteSetNull());
        Query index = dsl.createIndex(name(DynamicNames.constraintName("ix", tableName, column)))
                .on(table(name(tableName)), field(name(column)));
        return execute(foreignKey, index);
    }

    public List<String> addUniqueIndex(String tableName, String column) {
        return execute(dsl.createUniqueIndex(name(DynamicNames.constraintName("uq", tableName, column)))
                .on(table(name(tableName)), field(name(column))));
    }

    private List<String> execute(Query... queries) {
        List<String> executed = new ArrayList<>();
        for (Query query : queries) {
            String sql = dsl.renderInlined(query);
            query.execute();
            executed.add(sql);
        }
        return executed;
    }
}
