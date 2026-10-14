package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailContextPromptTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private static ReplyThread.Message message(boolean own, String from, String subject, String sentAt, String body) {
        return new ReplyThread.Message(own, from, from.toLowerCase() + "@acme.com", List.of("me@example.com"), subject,
                Instant.parse(sentAt), body);
    }

    private static final ReplyThread.Message INVITE = message(false, "Anna", "Interview", "2026-10-01T08:00:00Z",
            "We invite you to an interview on Friday at Hauptstrasse 5.");
    private static final ReplyThread.Message ANSWER = message(true, "Me", "Re: Interview", "2026-10-01T09:00:00Z", "Friday works.");
    private static final ReplyThread.Message EARLIER_FROM_ANNA = message(false, "Anna", "Your application", "2026-09-20T08:00:00Z",
            "Thank you for your application as backend engineer.");
    private static final ReplyThread.Message RELATED = message(false, "Jobs", "Application received", "2026-09-15T08:00:00Z",
            "We received your application.");

    private static MailContext full() {
        return new MailContext(List.of(INVITE, ANSWER), List.of(EARLIER_FROM_ANNA), List.of(RELATED),
                List.of("events: Interview at Acme", "people: Anna Berger"));
    }

    @Test
    void an_empty_context_renders_nothing() {
        assertThat(MailContextPrompt.render(MailContext.EMPTY, ZONE, 10_000)).isEmpty();
        assertThat(MailContext.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void renders_records_thread_sender_history_and_related_mail_with_the_rule_to_record_only_the_email_itself() {
        String text = MailContextPrompt.render(full(), ZONE, 10_000);

        assertThat(text).contains("for context only").contains("record only what the email below states")
                .contains("Already recorded").contains("events: Interview at Acme").contains("people: Anna Berger")
                .contains("Earlier in this conversation").contains("Hauptstrasse 5").contains("From: You")
                .contains("From the same sender").contains("Your application")
                .contains("Related emails").contains("Application received");
        // local time with weekday, like the email itself
        assertThat(text).contains("Thursday, 2026-10-01 10:00 +02:00");
        assertThat(text.indexOf("Hauptstrasse")).isLessThan(text.indexOf("Friday works."));
    }

    @Test
    void a_tight_budget_drops_related_mail_first_then_sender_history_then_the_oldest_thread_messages() {
        String all = MailContextPrompt.render(full(), ZONE, 10_000);
        String withoutRelated = MailContextPrompt.render(new MailContext(full().earlierInThread(), full().fromSender(), List.of(),
                full().records()), ZONE, 10_000);
        String threadOnly = MailContextPrompt.render(new MailContext(full().earlierInThread(), List.of(), List.of(), full().records()),
                ZONE, 10_000);

        assertThat(MailContextPrompt.render(full(), ZONE, all.length() - 1)).doesNotContain("Application received")
                .contains("Your application");
        assertThat(MailContextPrompt.render(full(), ZONE, withoutRelated.length() - 1)).doesNotContain("Your application")
                .contains("Hauptstrasse");
        assertThat(MailContextPrompt.render(full(), ZONE, threadOnly.length() - 1)).doesNotContain("Hauptstrasse")
                .contains("Friday works.").contains("events: Interview at Acme");
    }

    @Test
    void long_bodies_are_cut_to_an_excerpt() {
        String body = "word ".repeat(2000);
        String text = MailContextPrompt.render(new MailContext(List.of(), List.of(), List.of(message(false, "Jobs", "Long", "2026-09-15T08:00:00Z",
                body)), List.of()), ZONE, 100_000);

        assertThat(text.length()).isLessThan(1000);
        assertThat(text).contains("…");
    }
}
