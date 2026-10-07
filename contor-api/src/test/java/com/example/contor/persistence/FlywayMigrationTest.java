package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void enables_vector_and_trigram_extensions() {
        var extensions = jdbcTemplate.queryForList("select extname from pg_extension", String.class);

        assertThat(extensions).contains("vector", "pg_trgm");
    }

    @Test
    void creates_spring_session_tables() {
        assertThat(tableExists("spring_session")).isTrue();
        assertThat(tableExists("spring_session_attributes")).isTrue();
    }

    @Test
    void creates_the_read_only_role_without_login() {
        var canLogin = jdbcTemplate.queryForObject(
                "select rolcanlogin from pg_roles where rolname = 'app_readonly'", Boolean.class);

        assertThat(canLogin).isFalse();
    }

    @Test
    void enum_columns_are_guarded_by_check_constraints() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into \"proposal\" (id, kind, status, payload, created_at) values (nextval('proposal_seq'), 'NOPE', 'PENDING', '{}', now())"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_proposal_kind");
    }

    @Test
    void email_subject_has_a_trigram_index_and_embeddings_an_hnsw_index() {
        var indexes = jdbcTemplate.queryForList("select indexdef from pg_indexes where schemaname = 'public'", String.class);

        assertThat(indexes).anyMatch(def -> def.contains("email_message") && def.contains("gin_trgm_ops"));
        assertThat(indexes).anyMatch(def -> def.contains("email_embedding") && def.contains("hnsw"));
    }

    private boolean tableExists(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)",
                Boolean.class, name));
    }
}
