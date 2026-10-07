package com.example.contor;

import com.example.contor.config.CryptoConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @DataJpaTest} against the shared Testcontainers Postgres (Flyway-migrated, Hibernate validates the schema),
 * with the encryption bean the {@code *_enc} converters need.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@Import({PostgresTestcontainersConfiguration.class, CryptoConfig.class})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public @interface RepositoryTest {
}
