package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A user's AI permissions (setup wizard). No row means {@link #defaults}: no mail access, everything SUGGEST, reply drafts OFF. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"ai_settings\"")
public class AiSettings extends AuditableEntity {

    public static final int DEFAULT_HISTORY_DAYS = 90;

    @EmbeddedId
    private AiSettingsId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Builder.Default
    @Column(name = "mail_access", nullable = false)
    private boolean mailAccess = false;

    /** Days the inbox analysis reads back; 0 = everything. */
    @Builder.Default
    @Column(name = "history_days", nullable = false)
    private int historyDays = DEFAULT_HISTORY_DAYS;

    /** Creating collections (CREATE_COLLECTION). */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiPermissionLevel categories = AiPermissionLevel.SUGGEST;

    /** Records of People, Organizations and the other non-calendar, non-task collections. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiPermissionLevel records = AiPermissionLevel.SUGGEST;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiPermissionLevel calendar = AiPermissionLevel.SUGGEST;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiPermissionLevel tasks = AiPermissionLevel.SUGGEST;

    /** Reply drafts (DRAFT_REPLY): OFF or SUGGEST, never AUTO (a draft is never sent). */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiPermissionLevel replies = AiPermissionLevel.OFF;

    /** Also suggest optional polite replies (rejections, thank-yous); only with {@link #replies} SUGGEST. */
    @Builder.Default
    @Column(name = "courtesy_replies", nullable = false)
    private boolean courtesyReplies = false;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    /** Who the user is in their own words (role, company, customers and suppliers); tells the AI whose side the mail is on. */
    @Column(name = "owner_profile")
    private String ownerProfile;

    /** Unsaved defaults for a user without a settings row. */
    public static AiSettings defaults(AppUser user) {
        return AiSettings.builder().user(user).build();
    }

    @Embeddable
    public record AiSettingsId(@GeneratedValue Long id) {}
}
