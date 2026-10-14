package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.ActorType;
import com.example.contor.domain.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void can_save_and_find_by_action_with_json_snapshots() {
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType(ActorType.SYSTEM)
                .action("COLLECTION_CREATED")
                .entityType("collection")
                .entityId(7L)
                .after(Map.of("slug", "deliveries", "schemaVersion", 1))
                .build());

        var entries = auditLogRepository.findByActionOrderByOccurredAtDesc("COLLECTION_CREATED");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getId().id()).isNotNull();
        assertThat(entries.get(0).getAfter()).containsEntry("slug", "deliveries");
        assertThat(entries.get(0).getBefore()).isNull();
    }

    @Test
    void audit_log_rejects_updates_in_the_database() {
        var id = givenEntry().getId().id();

        assertThatThrownBy(() -> jdbcTemplate.update("update \"audit_log\" set action = 'X' where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void audit_log_rejects_deletes_in_the_database() {
        var id = givenEntry().getId().id();

        assertThatThrownBy(() -> jdbcTemplate.update("delete from \"audit_log\" where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private AuditLog givenEntry() {
        return auditLogRepository.saveAndFlush(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType(ActorType.USER)
                .action("AUTH_LOGIN_SUCCESS")
                .build());
    }
}
