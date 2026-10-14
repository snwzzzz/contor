package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"email_message\"")
public class EmailMessage extends AuditableEntity {

    /** Values of {@code folder}: the synced Graph folders. Sent mail is context only (never AI-processed, never listed). */
    public static final String FOLDER_INBOX = "inbox";
    public static final String FOLDER_SENT = "sent";

    @EmbeddedId
    private EmailMessageId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private MailAccount account;

    @Column(name = "provider_message_id", nullable = false)
    private String providerMessageId;

    @Column(name = "internet_message_id")
    private String internetMessageId;

    @Column(name = "conversation_id")
    private String conversationId;

    private String folder;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "from_name")
    private String fromName;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "to_recipients", nullable = false)
    private List<EmailRecipient> toRecipients = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cc_recipients", nullable = false)
    private List<EmailRecipient> ccRecipients = new ArrayList<>();

    private String subject;

    private String snippet;

    @Column(name = "body_text")
    private String bodyText;

    @Column(name = "body_html")
    private String bodyHtml;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<EmailHeader> headers = new ArrayList<>();

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Builder.Default
    @Column(name = "has_attachments", nullable = false)
    private boolean hasAttachments = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_state", nullable = false)
    private ProcessingState processingState = ProcessingState.NEW;

    @Builder.Default
    @Column(name = "processing_version", nullable = false)
    private int processingVersion = 0;

    @Column(name = "processing_error")
    private String processingError;

    @Embeddable
    public record EmailMessageId(@GeneratedValue Long id) {}
}
