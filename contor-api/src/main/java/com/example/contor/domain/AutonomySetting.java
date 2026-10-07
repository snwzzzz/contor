package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

/** Autonomy level per proposal kind and scope. No setting means {@link AutonomyLevel#SUGGEST}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"autonomy_setting\"")
public class AutonomySetting extends AuditableEntity {

    @EmbeddedId
    private AutonomySettingId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_kind", nullable = false)
    private ProposalKind actionKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private AutonomyScopeType scopeType;

    /** Id of the collection/label/rule; {@code null} for GLOBAL. */
    @Column(name = "scope_id")
    private Long scopeId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutonomyLevel level = AutonomyLevel.SUGGEST;

    @Embeddable
    public record AutonomySettingId(@GeneratedValue Long id) {}
}
