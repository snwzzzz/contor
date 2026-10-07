package com.example.contor;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@code @JooqTest} against the shared Testcontainers Postgres with Flyway migrations (each test rolls back, DDL included). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@JooqTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(PostgresTestcontainersConfiguration.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public @interface DynamicTableTest {
}
