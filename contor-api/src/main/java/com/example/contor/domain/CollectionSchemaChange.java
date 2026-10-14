package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** Append-only history of generated DDL per collection (a DB trigger rejects updates and deletes). Detail table. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Immutable
@Table(name = "\"collection_schema_change\"")
public class CollectionSchemaChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "from_version", nullable = false)
    private int fromVersion;

    @Column(name = "to_version", nullable = false)
    private int toVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> change;

    @Column(name = "ddl_executed", nullable = false)
    private String ddlExecuted;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    /** {@code null} when executed by the system (e.g. seeding). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executed_by")
    private AppUser executedBy;
}
