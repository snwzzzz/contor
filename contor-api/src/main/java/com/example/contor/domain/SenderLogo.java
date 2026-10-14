package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Cached icon of a sender domain; {@code image == null} records that the domain has none. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"sender_logo\"")
public class SenderLogo extends AuditableEntity {

    @EmbeddedId
    private SenderLogoId id;

    @Column(nullable = false, unique = true)
    private SenderDomain domain;

    private byte[] image;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Embeddable
    public record SenderLogoId(@GeneratedValue Long id) {}
}
