package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/** Kind of relation between records. {@code null} from/to collection means "any collection". */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"relation_type\"")
public class RelationType extends AuditableEntity {

    @EmbeddedId
    private RelationTypeId id;

    @Column(nullable = false, unique = true)
    private Slug slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "inverse_name")
    private String inverseName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_collection_id")
    private Collection fromCollection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_collection_id")
    private Collection toCollection;

    @Builder.Default
    @Column(nullable = false)
    private boolean directed = true;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_schema", nullable = false)
    private Map<String, Object> attributesSchema = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false)
    private CreatedBy createdBy;

    @Embeddable
    public record RelationTypeId(@GeneratedValue Long id) {}
}
