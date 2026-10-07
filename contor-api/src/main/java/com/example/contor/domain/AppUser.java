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
@Table(name = "\"app_user\"")
public class AppUser extends AuditableEntity {

    @EmbeddedId
    private AppUserId id;

    @Column(nullable = false, unique = true)
    private Username username;

    @Column(nullable = false)
    private Email email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "totp_secret_enc")
    private String totpSecret;

    @Builder.Default
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Consecutive lockouts; each one doubles the lock duration. */
    @Builder.Default
    @Column(name = "lockout_count", nullable = false)
    private int lockoutCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @Embeddable
    public record AppUserId(@GeneratedValue Long id) {}
}
