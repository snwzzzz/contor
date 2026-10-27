package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A question (USER) or an answer (ASSISTANT) of a chat conversation, at {@link #position}. An answer is written in the
 * background ({@link #state}); when DONE it has its {@link #blocks}, {@link #mentions} and {@link #citations}.
 * Dynamic updates: the answerer writes the state while the API may read or delete the conversation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@DynamicUpdate
@Table(name = "\"chat_message\"")
public class ChatMessage extends AuditableEntity {

    @EmbeddedId
    private ChatMessageId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRole role;

    /** The question, or the answer as plain text (for the history the next turn sees). */
    private String content;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessageState state = ChatMessageState.PENDING;

    /** Why a FAILED answer failed, in words for the user. */
    private String error;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ChatBlock> blocks = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ChatMention> mentions = new ArrayList<>();

    /** The validated retrieval plan of an answer (for debugging and the evaluation). */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> plan;

    private String model;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "plan_ms")
    private Integer planMs;

    @Column(name = "answer_ms")
    private Integer answerMs;

    @Builder.Default
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("marker")
    private List<ChatCitation> citations = new ArrayList<>();

    @Embeddable
    public record ChatMessageId(@GeneratedValue Long id) {}
}
