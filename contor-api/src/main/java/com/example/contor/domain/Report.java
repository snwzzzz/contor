package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An AI report about one record ({@code collection}, {@code recordId}) and the connected records in its {@link #scope},
 * or — with {@code recordId} {@code null} — about the records of {@code collection} its scope's filters select.
 * A follow-up question is a report of its own with {@link #parentReport} set. Dynamic updates: the generator writes the
 * status while the API may read or delete the report.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@DynamicUpdate
@Table(name = "\"report\"")
public class Report extends AuditableEntity {

    @EmbeddedId
    private ReportId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    /** {@code null} for a report over the whole collection. */
    @Column(name = "record_id")
    private Long recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_report_id")
    private Report parentReport;

    /** What the user asked; {@code null} = the default summary. */
    private String prompt;

    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ReportScope scope;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.QUEUED;

    @Column(name = "failure_reason")
    private String failureReason;

    private String model;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Builder.Default
    @Column(name = "claims_total", nullable = false)
    private int claimsTotal = 0;

    @Builder.Default
    @Column(name = "claims_verified", nullable = false)
    private int claimsVerified = 0;

    @Builder.Default
    @Column(name = "claims_removed", nullable = false)
    private int claimsRemoved = 0;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdByUser;

    @Builder.Default
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ReportSection> sections = new ArrayList<>();

    @Embeddable
    public record ReportId(@GeneratedValue Long id) {}
}
