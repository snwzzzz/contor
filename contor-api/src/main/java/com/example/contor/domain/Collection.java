package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata of a dynamic collection. Its records live in the generated table {@code tableName},
 * which is accessed only through {@code persistence/dynamic} (jOOQ), never through JPA.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"collection\"")
public class Collection extends AuditableEntity {

    @EmbeddedId
    private CollectionId id;

    @Column(nullable = false, unique = true)
    private Slug slug;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "table_name", nullable = false, unique = true)
    private String tableName;

    private String icon;

    @Builder.Default
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    /** Field slugs used to recognise an existing record (deduplication). */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_keys", nullable = false)
    private List<String> matchKeys = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "display_config", nullable = false)
    private DisplayConfig displayConfig = new DisplayConfig(null, null, null, null);

    @Builder.Default
    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false)
    private CreatedBy createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Builder.Default
    @OneToMany(mappedBy = "collection", orphanRemoval = true)
    @OrderBy("position")
    private List<CollectionField> fields = new ArrayList<>();

    @Embeddable
    public record CollectionId(@GeneratedValue Long id) {}
}
