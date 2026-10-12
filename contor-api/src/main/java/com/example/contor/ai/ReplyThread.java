package com.example.contor.ai;

import java.time.Instant;
import java.util.List;

/**
 * A conversation as the reply models read it, detached from JPA so the model calls run outside any transaction. The
 * last message is the email that is answered.
 *
 * @param userName display name of the user's mailbox (the reply is signed with its first name)
 * @param mailbox  address of the mailbox the conversation is in
 * @param messages oldest first; bodies already cleaned ({@link EmailTextCleaner})
 */
public record ReplyThread(String userName, String mailbox, List<Message> messages) {

    public ReplyThread {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("A reply thread needs the email that is answered");
        }
        messages = List.copyOf(messages);
    }

    /** @param own written by the user (Sent Items) */
    public record Message(boolean own, String fromName, String fromAddress, List<String> to, String subject, Instant sentAt,
                          String body) {
    }

    /** The email the reply answers. */
    public Message answered() {
        return messages.getLast();
    }

    /** Up to {@code count} messages before the answered one, oldest first. */
    public List<Message> before(int count) {
        int end = messages.size() - 1;
        return messages.subList(Math.max(0, end - count), end);
    }
}
