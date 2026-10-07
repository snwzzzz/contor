package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Append-only audit trail entry (a DB trigger rejects updates and deletes). Has {@code occurred_at} instead of auditing columns.
 * A record of a generated collection table is referenced as ({@code collectionId}, {@code entityId}).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Immutable
@Table(name = "\"audit_log\"")
public class AuditLog {

    @EmbeddedId
    private AuditLogId id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "collection_id")
    private Long collectionId;

    @Column(name = "entity_id")
    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> before;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> after;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Embeddable
    public record AuditLogId(@GeneratedValue Long id) {}
}
