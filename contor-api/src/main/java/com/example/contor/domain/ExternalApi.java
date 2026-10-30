package com.example.contor.domain;

import com.example.contor.persistence.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An external API a user connected ("App", ADR 0012); private to its {@link #owner}. contor calls its confirmed
 * {@link #endpoints} every {@link #pollIntervalMinutes} while it is {@link #enabled} and its {@link #status} allows it.
 * {@link #secret} is the encrypted JSON of the credentials and secret header values: never mapped to a DTO, logged or
 * put in a prompt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"external_api\"")
public class ExternalApi extends AuditableEntity {

    @EmbeddedId
    private ExternalApiId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Slug slug;

    private String description;

    @Column(name = "base_url", nullable = false)
    private BaseUrl baseUrl;

    @Column(name = "docs_url")
    private HttpsUrl docsUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_kind", nullable = false)
    private ApiAuthKind authKind = ApiAuthKind.NONE;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_config", nullable = false)
    private ApiAuthConfig authConfig = ApiAuthConfig.NONE;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret_enc")
    private String secret;

    /** Plain headers sent with every call. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ApiHeader> headers = new ArrayList<>();

    @Builder.Default
    @Column(name = "poll_interval_minutes", nullable = false)
    private int pollIntervalMinutes = 360;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /** Whether the assistant may learn from the answers (mappings, insights). */
    @Builder.Default
    @Column(name = "ai_analysis", nullable = false)
    private boolean aiAnalysis = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiStatus status = ApiStatus.ACTIVE;

    @Column(name = "last_called_at")
    private Instant lastCalledAt;

    @Column(name = "next_call_at")
    private Instant nextCallAt;

    @Builder.Default
    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    /** The last failure in words for the user, masked (never a URL or credential). */
    @Column(name = "last_error")
    private String lastError;

    @Builder.Default
    @Column(name = "daily_request_limit", nullable = false)
    private int dailyRequestLimit = 200;

    @Builder.Default
    @OneToMany(mappedBy = "api", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name")
    private List<ExternalApiEndpoint> endpoints = new ArrayList<>();

    @Embeddable
    public record ExternalApiId(@GeneratedValue Long id) {}
}
