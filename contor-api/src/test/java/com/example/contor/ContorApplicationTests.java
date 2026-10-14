package com.example.contor;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(PostgresTestcontainersConfiguration.class)
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ContorApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void context_loads() {
    }

    @Test
    void database_offers_the_pgvector_extension() {
        var available = jdbcTemplate.queryForObject(
                "select count(*) from pg_available_extensions where name = 'vector'", Integer.class);
        assertThat(available).isEqualTo(1);
    }
}
