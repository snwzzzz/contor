package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"label\"")
public class Label extends AuditableEntity {

    @EmbeddedId
    private LabelId id;

    @Column(nullable = false, unique = true)
    private Slug slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private HexColor color;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false)
    private CreatedBy createdBy;

    @Embeddable
    public record LabelId(@GeneratedValue Long id) {}
}
