package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * A confirmed endpoint of an {@link ExternalApi}: {@link #path} below the base URL with a fixed {@link #query} (and a
 * {@link #body} for a POST read query). {@link #itemsPath} finds the items in the answer, {@link #itemKeyPath} the
 * key that identifies an item across calls.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"external_api_endpoint\"")
public class ExternalApiEndpoint extends AuditableEntity {

    @EmbeddedId
    private ExternalApiEndpointId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_id", nullable = false)
    private ExternalApi api;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiHttpMethod method = ApiHttpMethod.GET;

    @Column(nullable = false)
    private ApiPath path;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> query = new HashMap<>();

    /** POST only. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> body;

    @Column(name = "items_path")
    private JsonPathExpr itemsPath;

    @Column(name = "item_key_path")
    private JsonPathExpr itemKeyPath;

    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EndpointSource source = EndpointSource.USER;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Embeddable
    public record ExternalApiEndpointId(@GeneratedValue Long id) {}
}
