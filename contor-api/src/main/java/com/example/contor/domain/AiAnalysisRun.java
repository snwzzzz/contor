package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

/**
 * One inbox analysis of a user (discovery, optional review, extraction) with its progress counters. Dynamic updates:
 * the worker writes counters while the API may cancel the run, so an update only writes the columns it changed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@DynamicUpdate
@Table(name = "\"ai_analysis_run\"")
public class AiAnalysisRun extends AuditableEntity {

    @EmbeddedId
    private AiAnalysisRunId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisRunStatus status = AnalysisRunStatus.QUEUED;

    /** Oldest email included; {@code null} = everything. */
    private Instant since;

    /** Model calls of the category discovery (batches + merge); 0 until it starts. */
    @Builder.Default
    @Column(name = "discovery_steps", nullable = false)
    private int discoverySteps = 0;

    @Builder.Default
    @Column(name = "discovery_steps_done", nullable = false)
    private int discoveryStepsDone = 0;

    @Builder.Default
    @Column(name = "total_emails", nullable = false)
    private int totalEmails = 0;

    @Builder.Default
    @Column(name = "processed_emails", nullable = false)
    private int processedEmails = 0;

    @Builder.Default
    @Column(name = "failed_emails", nullable = false)
    private int failedEmails = 0;

    @Builder.Default
    @Column(name = "proposals_created", nullable = false)
    private int proposalsCreated = 0;

    @Builder.Default
    @Column(name = "auto_applied", nullable = false)
    private int autoApplied = 0;

    @Builder.Default
    @Column(name = "categories_discovered", nullable = false)
    private int categoriesDiscovered = 0;

    /** The email being analyzed right now; {@code null} outside extraction. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_email_id")
    private EmailMessage currentEmail;

    private String error;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Embeddable
    public record AiAnalysisRunId(@GeneratedValue Long id) {}
}
