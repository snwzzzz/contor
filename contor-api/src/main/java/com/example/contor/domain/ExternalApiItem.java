package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * An item found in an {@link ExternalApiEndpoint}'s answers, identified by {@link #itemKey}: its last {@link #data}
 * and hash, when it was first and last seen or disappeared, and the record ({@link #collection}, {@link #recordId}) it
 * became. Detail table: identity ID, no auditing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"external_api_item\"")
public class ExternalApiItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private ExternalApiEndpoint endpoint;

    @Column(name = "item_key", nullable = false)
    private String itemKey;

    @Column(name = "item_hash", nullable = false)
    private String itemHash;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> data = new HashMap<>();

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** Set when the item is no longer in the answer. */
    @Column(name = "gone_at")
    private Instant goneAt;

    /** Set together with {@link #recordId}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    @Column(name = "record_id")
    private Long recordId;
}
