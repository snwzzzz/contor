package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Label on an email. Composite key (email, label), no own identity, no auditing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"email_label\"")
public class EmailLabel {

    @EmbeddedId
    private EmailLabelId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabelSource source;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Embeddable
    public record EmailLabelId(@Column(name = "email_id") Long emailId, @Column(name = "label_id") Long labelId) {}
}
