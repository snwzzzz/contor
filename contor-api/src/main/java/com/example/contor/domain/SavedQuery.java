package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"saved_query\"")
public class SavedQuery extends AuditableEntity {

    @EmbeddedId
    private SavedQueryId id;

    @Column(nullable = false)
    private String name;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> dsl;

    @Builder.Default
    @Column(nullable = false)
    private boolean pinned = false;

    @Embeddable
    public record SavedQueryId(@GeneratedValue Long id) {}
}
