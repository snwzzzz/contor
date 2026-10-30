package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One call of an {@link ExternalApiEndpoint}, for the history and the daily request limit (pruned after 30 days).
 * {@link #error} is masked: never a URL or a credential. Detail table: identity ID, no auditing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"external_api_call\"")
public class ExternalApiCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private ExternalApiEndpoint endpoint;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    /** {@code null} when no answer came (refused, timeout, network error). */
    @Column(name = "http_status")
    private Integer httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallOutcome outcome;

    private String error;

    @Builder.Default
    @Column(name = "items_new", nullable = false)
    private int itemsNew = 0;

    @Builder.Default
    @Column(name = "items_changed", nullable = false)
    private int itemsChanged = 0;

    @Builder.Default
    @Column(name = "items_gone", nullable = false)
    private int itemsGone = 0;
}
