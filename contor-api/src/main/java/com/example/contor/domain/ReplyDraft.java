package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** A reply the user kept from a DRAFT_REPLY proposal (possibly edited). Never sent; copied or opened in the mail app. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"reply_draft\"")
public class ReplyDraft extends AuditableEntity {

    @EmbeddedId
    private ReplyDraftId id;

    /** The email this draft answers. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private EmailMessage email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private MailAccount account;

    @Column(name = "conversation_id")
    private String conversationId;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_recipients", nullable = false)
    private List<EmailRecipient> toRecipients = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cc_recipients", nullable = false)
    private List<EmailRecipient> ccRecipients = new ArrayList<>();

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReplyDraftStatus status = ReplyDraftStatus.READY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id")
    private Proposal proposal;

    @Embeddable
    public record ReplyDraftId(@GeneratedValue Long id) {}
}
