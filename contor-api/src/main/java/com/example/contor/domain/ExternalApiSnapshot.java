package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An answer of an {@link ExternalApiEndpoint} as received (body only, no request headers), kept for the last few calls.
 * An answer with the same {@link #contentHash} as the latest one is not stored again. Detail table: identity ID, no
 * auditing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"external_api_snapshot\"")
public class ExternalApiSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private ExternalApiEndpoint endpoint;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "content_type")
    private String contentType;

    @Column(nullable = false)
    private String body;

    /** The answer was longer than the size cap and is cut there. */
    @Builder.Default
    @Column(nullable = false)
    private boolean truncated = false;
}
