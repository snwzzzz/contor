package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/** A section of a report. Detail table: identity ID, no auditing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"report_section\"")
public class ReportSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportSectionKind kind;

    @Column(nullable = false)
    private String title;

    @Builder.Default
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ReportFinding> findings = new ArrayList<>();

    /** Sentences outside findings (summary, open questions…); the sentences of a finding are in its own list. */
    @Builder.Default
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("finding_id is null")
    @OrderBy("position")
    private List<ReportClaim> claims = new ArrayList<>();
}
