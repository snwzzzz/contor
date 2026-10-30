package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * How the items of an {@link ExternalApiEndpoint} become records of a {@link Collection}: the title from
 * {@link #titlePath}, each field from its {@link ApiFieldMapping}. The model proposes it, the user approves it (ACTIVE);
 * from then on polls apply it in code.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"external_api_mapping\"")
public class ExternalApiMapping extends AuditableEntity {

    @EmbeddedId
    private ExternalApiMappingId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private ExternalApiEndpoint endpoint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "title_path")
    private JsonPathExpr titlePath;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_map", nullable = false)
    private List<ApiFieldMapping> fieldMap = new ArrayList<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MappingState state = MappingState.PROPOSED;

    @Embeddable
    public record ExternalApiMappingId(@GeneratedValue Long id) {}
}
