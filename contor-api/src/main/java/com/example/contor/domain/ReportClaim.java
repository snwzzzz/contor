package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** One sentence of a report with its fact-check result and sources. Detail table: identity ID, no auditing. */
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
