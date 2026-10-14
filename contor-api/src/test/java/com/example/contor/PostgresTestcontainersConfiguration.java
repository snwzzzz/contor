package com.example.contor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Postgres + pgvector container for every persistence and integration test.
 * Reused across runs when {@code testcontainers.reuse.enable=true} is set in {@code ~/.testcontainers.properties}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    public static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.7-pg18-trixie")
            .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        // -Dcontor.testcontainers.reuse=false forces a fresh database (end-to-end tests need a pending first-run setup).
        return new PostgreSQLContainer(PGVECTOR_IMAGE)
                .withReuse(Boolean.parseBoolean(System.getProperty("contor.testcontainers.reuse", "true")));
    }
}
