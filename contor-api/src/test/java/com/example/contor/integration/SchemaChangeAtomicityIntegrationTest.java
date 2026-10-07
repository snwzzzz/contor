package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.domain.Slug;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.service.AuditEvent;
import com.example.contor.service.AuditService;
import com.example.contor.service.CollectionSchemaService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Metadata, generated DDL, history and audit commit or roll back together (PostgreSQL transactional DDL). */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SchemaChangeAtomicityIntegrationTest {

    @Autowired
    private CollectionSchemaService schemaService;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditService auditService;

    @Test
    void a_failure_after_the_ddl_rolls_back_table_metadata_and_history() {
        doThrow(new IllegalStateException("audit unavailable")).when(auditService).record(any(AuditEvent.class));

        assertThatThrownBy(() -> schemaService.createCollection(new NewCollectionCommand("parcels", "Parcels")
                .fields(List.of(new NewCollectionFieldCommand("tracking", "Tracking", CollectionFieldType.TEXT)))))
                .hasMessage("audit unavailable");

        assertThat(jdbcTemplate.queryForObject("select to_regclass('public.c_parcels') is not null", Boolean.class)).isFalse();
        assertThat(collectionRepository.findBySlug(new Slug("parcels"))).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from collection_schema_change c join collection col on col.id = c.collection_id where col.slug = 'parcels'",
                Long.class)).isZero();
    }
}
