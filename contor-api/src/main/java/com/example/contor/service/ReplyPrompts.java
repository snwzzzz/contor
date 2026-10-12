package com.example.contor.service;

import com.example.contor.ai.ReplyThread;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** How the reply models read a conversation: one block per message, the user's own ones marked, the answered one last. */
final class ReplyPrompts {

    /** One message never takes more than this (long quotes and footers are cut). */
    static final int MAX_MESSAGE_CHARS = 3000;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx");

    private ReplyPrompts() {
    }

    /**
     * The conversation, oldest first, within {@code budget} characters: the answered email always, earlier messages
     * from the newest backwards while they fit; left-out ones are mentioned.
     */
    static String thread(ReplyThread thread, ZoneId zone, int budget) {
        List<ReplyThread.Message> messages = thread.messages();
        String answered = message(thread.answered(), zone, "ANSWER THIS");
        Deque<String> blocks = new ArrayDeque<>();
        int used = answered.length();
        int left = 0;
        for (int i = messages.size() - 2; i >= 0; i--) {
            String block = message(messages.get(i), zone, null);
            if (left > 0 || used + block.length() > budget) {
                left++;
                continue;
            }
            blocks.addFirst(block);
            used += block.length();
        }
        StringBuilder text = new StringBuilder();
        if (left > 0) {
            text.append("[").append(left).append(left == 1 ? " earlier message" : " earlier messages").append(" left out]\n\n");
        }
        blocks.forEach(block -> text.append(block).append('\n'));
        return text.append(answered).toString();
    }

    /** Only the answered email and up to {@code earlier} messages before it (the triage call). */
    static String recent(ReplyThread thread, ZoneId zone, int earlier) {
        StringBuilder text = new StringBuilder();
        thread.before(earlier).forEach(message -> text.append(message(message, zone, null)).append('\n'));
        return text.append(message(thread.answered(), zone, "ANSWER THIS")).toString();
    }

    static String message(ReplyThread.Message message, ZoneId zone, String marker) {
        String sender = message.own() ? "You" : Optional.ofNullable(message.fromName()).filter(name -> !name.isBlank())
                .map(name -> name + " <" + Optional.ofNullable(message.fromAddress()).orElse("") + ">")
                .orElse(Optional.ofNullable(message.fromAddress()).orElse("unknown"));
        String when = message.sentAt() != null
                ? message.sentAt().atZone(zone).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", "
                + message.sentAt().atZone(zone).format(WHEN)
                : "unknown";
        String body = Optional.ofNullable(message.body()).orElse("").strip();
        if (body.length() > MAX_MESSAGE_CHARS) {
            body = body.substring(0, MAX_MESSAGE_CHARS) + "\n[…truncated]";
        }
        StringBuilder text = new StringBuilder("--- ");
        if (marker != null) {
            text.append(marker).append(": ");
        }
        text.append("From: ").append(sender).append('\n')
                .append("To: ").append(String.join(", ", Optional.ofNullable(message.to()).orElse(List.of()))).append('\n')
                .append("Date: ").append(when).append('\n')
                .append("Subject: ").append(Optional.ofNullable(message.subject()).orElse("")).append("\n\n")
                .append(body.isEmpty() ? "(no text)" : body).append('\n');
        return text.toString();
    }
}
