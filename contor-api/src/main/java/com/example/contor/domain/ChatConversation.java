package com.example.contor.domain;

import com.example.contor.domain.ReportScope.RecordRef;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * A user's conversation with the AI chat; private to its {@link #owner}. {@link #focus} holds the records the last
 * answer was about, so the next question can refer to them ("and what did he write?").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"chat_conversation\"")
public class ChatConversation extends AuditableEntity {

    @EmbeddedId
    private ChatConversationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    /** The first question, shortened; the user may rename it. */
    @Column(nullable = false)
    private String title;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<RecordRef> focus = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ChatMessage> messages = new ArrayList<>();

    @Embeddable
    public record ChatConversationId(@GeneratedValue Long id) {}
}
