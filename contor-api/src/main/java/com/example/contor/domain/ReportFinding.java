package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A key finding or timeline entry; its sentences are {@link #claims}. Detail table: identity ID, no auditing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"report_finding\"")
public class ReportFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private ReportSection section;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSeverity severity;

    /** When it happened (timeline entries). */
    @Column(name = "event_date")
    private Instant eventDate;

    /** Each claim also points at the finding's section. */
    @Builder.Default
    @OneToMany(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ReportClaim> claims = new ArrayList<>();
}
