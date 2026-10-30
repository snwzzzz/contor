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

    /** Provider sync cursor of the inbox (Graph delta link, Gmail history id); {@code null} = sync from scratch. */
    @Column(name = "sync_cursor")
    private String syncCursor;

    /** Provider sync cursor of the sent mail. */
    @Column(name = "sent_sync_cursor")
    private String sentSyncCursor;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /** Profile photo from the provider (small, a few KB). */
    @Column(name = "photo")
    private byte[] photo;

    @Column(name = "photo_content_type")
    private String photoContentType;

    /** When the photo was last requested; {@code null} = fetch on the next sync. */
    @Column(name = "photo_checked_at")
    private Instant photoCheckedAt;

    /** When the contact photos were last read from the provider; refreshed at most daily. */
    @Column(name = "contacts_checked_at")
    private Instant contactsCheckedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MailAccountStatus status = MailAccountStatus.ACTIVE;

    /** The AI reads this mailbox (the analysis and new mail); off = its mail is never given to the model. */
    @Builder.Default
    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled = true;

    /** Its mail is not shown in the app; whether the AI still reads it is {@link AiSettings#isReadHiddenMailboxes()}. */
    @Builder.Default
    @Column(nullable = false)
    private boolean hidden = false;

    @Embeddable
    public record MailAccountId(@GeneratedValue Long id) {}
}
