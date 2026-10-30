package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** One sentence, list item or table cell of a report with its sources (and, in older reports, its fact-check result). Detail table: identity ID, no auditing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"report_claim\"")
public class ReportClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private ReportSection section;

    /** {@code null} for sentences outside findings. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id")
    private ReportFinding finding;

    /** {@code null} for sentences outside blocks (older reports, findings, timeline). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    private ReportBlock block;

    /** Table cells only. */
    @Column(name = "row_index")
    private Integer rowIndex;

    @Column(name = "column_index")
    private Integer columnIndex;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimVerification verification;

    /** Why the verifier judged it so (shown in the fact-check details). */
    @Column(name = "verifier_note")
    private String verifierNote;

    @Builder.Default
    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("marker")
    private List<ReportCitation> citations = new ArrayList<>();
}
