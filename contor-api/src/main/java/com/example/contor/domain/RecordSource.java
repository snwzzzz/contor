package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Links a record ({@code collection}, {@code recordId}) to an email. Detail table: identity ID, only {@code created_at}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"record_source\"")
public class RecordSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private EmailMessage email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordSourceRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
