package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * A source of a chat answer, with the same targets as a {@link ReportCitation}: RECORD, FIELD (with the cited value as
 * {@code valueSnapshot.value}), EMAIL_PARAGRAPH (with the verbatim {@code quote}) or RELATION. Detail table: identity
 * ID, no auditing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"chat_citation\"")
public class ChatCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    /** The {@code [n]} shown in the answer; one number per distinct source. */
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
}
