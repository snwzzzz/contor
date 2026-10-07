package com.example.contor.domain;

import com.example.contor.persistence.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"mail_account\"")
public class MailAccount extends AuditableEntity {

    @EmbeddedId
    private MailAccountId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private AppUser owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MailProvider provider;

    @Column(name = "email_address", nullable = false)
    private Email emailAddress;

    @Column(name = "display_name")
    private String displayName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token_enc")
    private String accessToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "refresh_token_enc")
    private String refreshToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "delta_link")
    private String deltaLink;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MailAccountStatus status = MailAccountStatus.ACTIVE;

    @Embeddable
    public record MailAccountId(@GeneratedValue Long id) {}
}
