package com.example.contor;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app locally against a Testcontainers Postgres, no compose needed: {@code ./mvnw spring-boot:test-run}.
 */
public class TestContorApplication {

    public static void main(String[] args) {
        SpringApplication.from(ContorApplication::main).with(PostgresTestcontainersConfiguration.class).run(args);
    }
}
