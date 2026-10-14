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

    /** Graph delta link of the Sent Items folder. */
    @Column(name = "sent_delta_link")
    private String sentDeltaLink;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /** Profile photo from Microsoft Graph (small, a few KB). */
    @Column(name = "photo")
    private byte[] photo;

    @Column(name = "photo_content_type")
    private String photoContentType;

    /** When the photo was last requested; {@code null} = fetch on the next sync. */
    @Column(name = "photo_checked_at")
    private Instant photoCheckedAt;

    /** When the Outlook contact photos were last read; refreshed at most daily. */
    @Column(name = "contacts_checked_at")
    private Instant contactsCheckedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MailAccountStatus status = MailAccountStatus.ACTIVE;

    @Embeddable
    public record MailAccountId(@GeneratedValue Long id) {}
}
