package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"proposal\"")
public class Proposal extends AuditableEntity {

    @EmbeddedId
    private ProposalId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalKind kind;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status = ProposalStatus.PENDING;

    /** What was proposed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload;

    /** What was actually applied (may differ after the user edited the proposal). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_payload")
    private Map<String, Object> finalPayload;

    /** What applying produced (e.g. the created record); dependent proposals and undo read it. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> result;

    /** AUTO proposal waiting for its dependencies: applied by the system once they are applied. */
    @Builder.Default
    @Column(name = "auto_apply_when_ready", nullable = false)
    private boolean autoApplyWhenReady = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_email_id")
    private EmailMessage sourceEmail;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    private String reasoning;

    private String model;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "schema_version")
    private Integer schemaVersion;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private AppUser decidedBy;

    @Embeddable
    public record ProposalId(@GeneratedValue Long id) {}
}
