package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Edge between two records, each addressed as (collection, record id). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"relation\"")
public class Relation extends AuditableEntity {

    @EmbeddedId
    private RelationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relation_type_id", nullable = false)
    private RelationType relationType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_collection_id", nullable = false)
    private Collection fromCollection;

    @Column(name = "from_record_id", nullable = false)
    private Long fromRecordId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_collection_id", nullable = false)
    private Collection toCollection;

    @Column(name = "to_record_id", nullable = false)
    private Long toRecordId;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> attributes = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_email_id")
    private EmailMessage sourceEmail;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false)
    private RelationCreatedBy createdBy;

    @Embeddable
    public record RelationId(@GeneratedValue Long id) {}
}
