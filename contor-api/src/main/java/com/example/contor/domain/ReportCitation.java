package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * A source of a report sentence, by {@link #type}: RECORD ({@code collection}, {@code recordId}), FIELD (plus
 * {@code fieldSlug} and the cited value as {@code valueSnapshot.value}), EMAIL_PARAGRAPH ({@code email},
 * {@code paragraphIndex}, the paragraph's hash and segmenter version, the verbatim {@code quote}) or RELATION
 * ({@code relationId}). Detail table: identity ID, no auditing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"report_citation\"")
public class ReportCitation implements Citation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private ReportClaim claim;

    /** The {@code [n]} shown in the report; one number per distinct source. */
    @Column(nullable = false)
    private int marker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CitationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "field_slug")
    private String fieldSlug;

    /** {@code null} once the email was deleted (the quote stays). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_id")
    private EmailMessage email;

    @Column(name = "paragraph_index")
    private Integer paragraphIndex;

    @Column(name = "paragraph_hash")
    private String paragraphHash;

    @Column(name = "segmenter_version")
    private Integer segmenterVersion;

    private String quote;

    @Column(name = "relation_id")
    private Long relationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_snapshot")
    private Map<String, Object> valueSnapshot;

    /** Shown as a quote under its finding (not only as a marker). */
    @Builder.Default
    @Column(name = "is_supporting_quote", nullable = false)
    private boolean supportingQuote = false;
}
