package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Field of a collection. {@code columnName} is derived from the slug; MONEY uses {@code <column>_amount}/{@code <column>_currency}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"collection_field\"")
public class CollectionField extends AuditableEntity {

    @EmbeddedId
    private CollectionFieldId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(nullable = false)
    private Slug slug;

    @Column(nullable = false)
    private String name;

    /** Also used in AI prompts. */
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldType type;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> options = new HashMap<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean required = false;

    @Builder.Default
    @Column(name = "unique_value", nullable = false)
    private boolean uniqueValue = false;

    @Column(nullable = false)
    private int position;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Embeddable
    public record CollectionFieldId(@GeneratedValue Long id) {}
}
