package com.example.contor.ai;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Renders a {@link MailContext} for a prompt within a character budget. Reading context is cheap compared with writing
 * the answer (step 3 of docs/ai-v2-plan.md), so the budget can be generous. When it does not fit, related emails go
 * first, then the sender history, then the oldest messages of the conversation; the record lines always stay.
 */
public final class MailContextPrompt {

    /** A message of the conversation is shown up to this length. */
    static final int THREAD_EXCERPT_CHARS = 1500;
    /** An email of the sender history or a related one is one line of this length. */
    static final int LINE_EXCERPT_CHARS = 300;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx");

    private static final String INTRO = """
            Earlier emails, for context only: record only what the email below states or changes. Use them to understand \
            it — who the people are, what "as discussed" refers to, which appointment is moved, which invoice is reminded \
            of. When the email changes a record that is already recorded, return it with exactly the same title (nothing \
            added like "moved") and the new values. Still record every new to-do or appointment the email asks for.
            """;

    private MailContextPrompt() {
    }

    /** The context as prompt text, or "" when there is none; at most {@code budget} characters unless the records alone exceed it. */
    public static String render(MailContext context, ZoneId zone, int budget) {
        if (context.isEmpty()) {
            return "";
        }
        List<ReplyThread.Message> thread = new ArrayList<>(context.earlierInThread());
        List<ReplyThread.Message> sender = new ArrayList<>(context.fromSender());
        List<ReplyThread.Message> related = new ArrayList<>(context.related());
        String text = text(context.records(), thread, sender, related, zone);
        while (text.length() > budget) {
            if (!related.isEmpty()) {
                related.removeLast();
            } else if (!sender.isEmpty()) {
                sender.removeLast();
            } else if (!thread.isEmpty()) {
                thread.removeFirst();
            } else {
                break;
            }
            text = text(context.records(), thread, sender, related, zone);
        }
        return text;
    }

    private static String text(List<String> records, List<ReplyThread.Message> thread, List<ReplyThread.Message> sender,
                               List<ReplyThread.Message> related, ZoneId zone) {
        StringBuilder text = new StringBuilder(INTRO);
        if (!records.isEmpty()) {
            text.append("\nAlready recorded from these emails:\n");
            records.forEach(record -> text.append("- ").append(record).append('\n'));
        }
        if (!thread.isEmpty()) {
            text.append("\nEarlier in this conversation (oldest first):\n");
            thread.forEach(message -> text.append(block(message, zone)));
        }
        if (!sender.isEmpty()) {
            text.append("\nFrom the same sender before:\n");
            sender.forEach(message -> text.append(line(message, zone)));
        }
        if (!related.isEmpty()) {
            text.append("\nRelated emails:\n");
            related.forEach(message -> text.append(line(message, zone)));
        }
        return text.toString();
    }

    private static String block(ReplyThread.Message message, ZoneId zone) {
        return "--- From: " + sender(message) + "\nDate: " + when(message, zone) + "\nSubject: "
                + Optional.ofNullable(message.subject()).orElse("") + "\n\n" + excerpt(message.body(), THREAD_EXCERPT_CHARS) + "\n";
    }

    private static String line(ReplyThread.Message message, ZoneId zone) {
        return "- " + when(message, zone) + " · " + sender(message) + " · " + Optional.ofNullable(message.subject()).orElse("")
                + ": " + excerpt(message.body(), LINE_EXCERPT_CHARS).replaceAll("\s+", " ") + "\n";
    }

    private static String sender(ReplyThread.Message message) {
        if (message.own()) {
            return "You";
        }
        String address = Optional.ofNullable(message.fromAddress()).orElse("");
        return Optional.ofNullable(message.fromName()).filter(name -> !name.isBlank()).map(name -> name + " <" + address + ">")
                .orElse(address.isEmpty() ? "unknown" : address);
    }

    private static String when(ReplyThread.Message message, ZoneId zone) {
        if (message.sentAt() == null) {
            return "unknown date";
        }
        var local = message.sentAt().atZone(zone);
        return local.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", " + local.format(WHEN);
    }

    private static String excerpt(String body, int max) {
        String text = Optional.ofNullable(body).orElse("").strip();
        if (text.isEmpty()) {
            return "(no text)";
        }
        return text.length() <= max ? text : text.substring(0, max).strip() + " …";
    }
}
