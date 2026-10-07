package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"rule\"")
public class Rule extends AuditableEntity {

    @EmbeddedId
    private RuleId id;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(nullable = false)
    private int priority = 100;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> conditions = new HashMap<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> actions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_from_proposal_id")
    private Proposal createdFromProposal;

    @Embeddable
    public record RuleId(@GeneratedValue Long id) {}
}
