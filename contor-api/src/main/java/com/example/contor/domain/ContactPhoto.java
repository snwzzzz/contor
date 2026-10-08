package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

/** Photo of an Outlook contact of a mailbox, by the contact's (lower-cased) email address. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"contact_photo\"")
public class ContactPhoto extends AuditableEntity {

    @EmbeddedId
    private ContactPhotoId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private MailAccount account;

    @Column(name = "email_address", nullable = false)
    private String emailAddress;

    @Column(nullable = false)
    private byte[] image;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Embeddable
    public record ContactPhotoId(@GeneratedValue Long id) {}
}
