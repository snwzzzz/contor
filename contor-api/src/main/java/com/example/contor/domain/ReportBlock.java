package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** A paragraph, list, quote or table of a report section. Detail table: identity ID, no auditing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"report_block\"")
public class ReportBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private ReportSection section;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportBlockKind kind;

    /** The header of a table; {@code null} or empty otherwise. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> columns;

    /** Sentences, list items or table cells (row by row); each also points at the block's section. */
    @Builder.Default
    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ReportClaim> claims = new ArrayList<>();
}
