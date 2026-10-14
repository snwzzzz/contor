package com.example.contor.integration;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Hibernate statement inspector for tests: remembers every SQL statement so a test can check what was read. */
public class SqlCapture implements StatementInspector {

    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    public static void clear() {
        STATEMENTS.clear();
    }

    public static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }
}
