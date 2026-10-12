package com.example.contor.ai;

import java.util.List;

/**
 * What the user's other mail says about an email, for the model (multi-mail understanding, ADR 0009). Detached from JPA
 * so the model call runs outside any transaction; bodies already cleaned ({@link EmailTextCleaner}).
 *
 * @param earlierInThread messages of the conversation before the email (the user's replies included), oldest first
 * @param fromSender      earlier emails from the same sender outside the conversation, newest first
 * @param related         emails close in meaning (embeddings), outside the conversation and the sender history, closest first
 * @param records         one line per record already created from any of these emails, e.g. {@code events: Interview at Acme}
 */
public record MailContext(List<ReplyThread.Message> earlierInThread, List<ReplyThread.Message> fromSender,
                          List<ReplyThread.Message> related, List<String> records) {

    public static final MailContext EMPTY = new MailContext(List.of(), List.of(), List.of(), List.of());

    public MailContext {
        earlierInThread = List.copyOf(earlierInThread);
        fromSender = List.copyOf(fromSender);
        related = List.copyOf(related);
        records = List.copyOf(records);
    }

    public boolean isEmpty() {
        return earlierInThread.isEmpty() && fromSender.isEmpty() && related.isEmpty() && records.isEmpty();
    }
}
