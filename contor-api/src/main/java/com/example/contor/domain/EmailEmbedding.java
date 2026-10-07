package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Embedding of an email per model. Composite key (email, model) so the embedding model can change. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"email_embedding\"")
public class EmailEmbedding {

    /** Vector dimension of {@code nomic-embed-text}; must match {@code vector(768)} in V1_0_4__mail.sql. */
    public static final int DIMENSIONS = 768;

    @EmbeddedId
    private EmailEmbeddingId id;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = DIMENSIONS)
    @Column(nullable = false)
    private float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Embeddable
    public record EmailEmbeddingId(@Column(name = "email_id") Long emailId, @Column(name = "model") String model) {}
}
