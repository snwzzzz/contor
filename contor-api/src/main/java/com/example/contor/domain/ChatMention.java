package com.example.contor.domain;

/**
 * A record or email a chat answer is about, shown as a link under the answer. Stored as JSON in
 * {@code chat_message.mentions}; the app builds mentions from the evidence, never from model text.
 *
 * @param collectionId   RECORD only
 * @param recordId       RECORD only
 * @param emailId        EMAIL only
 * @param label          the record's title or the email's subject (trimmed)
 * @param collectionName RECORD only, optional (for the chip)
 */
public record ChatMention(ChatMentionKind kind, Long collectionId, Long recordId, Long emailId, String label, String collectionName) {

    public ChatMention {
        if (kind == null) {
            throw new IllegalArgumentException("mention needs a kind");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("mention needs a label");
        }
        label = label.strip();
        boolean record = positive(collectionId) && positive(recordId) && emailId == null;
        boolean email = positive(emailId) && collectionId == null && recordId == null;
        if (kind == ChatMentionKind.RECORD ? !record : !email) {
            throw new IllegalArgumentException("mention needs exactly the ids of its kind");
        }
    }

    public static ChatMention record(Long collectionId, Long recordId, String label, String collectionName) {
        return new ChatMention(ChatMentionKind.RECORD, collectionId, recordId, null, label, collectionName);
    }

    public static ChatMention email(Long emailId, String label) {
        return new ChatMention(ChatMentionKind.EMAIL, null, null, emailId, label, null);
    }

    private static boolean positive(Long id) {
        return id != null && id > 0;
    }
}
