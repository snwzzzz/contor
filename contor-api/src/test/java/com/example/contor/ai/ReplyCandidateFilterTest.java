package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReplyCandidateFilterTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final Set<String> MINE = Set.of("me@example.com", "me@work.example");

    private static ReplyCandidateFilter.Candidate candidate(String from, List<String> to, String body) {
        return new ReplyCandidateFilter.Candidate(from, to, body, NOW.minus(Duration.ofDays(1)), MINE, false, false);
    }

    private static ReplyCandidateFilter.Candidate personal() {
        return candidate("anna@acme.com", List.of("Me@Example.com"), "Could you send me the slides by Friday?");
    }

    @Test
    void no_reply_and_notification_senders_are_automated() {
        assertThat(ReplyCandidateFilter.isAutomatedSender("no-reply@shop.example")).isTrue();
        assertThat(ReplyCandidateFilter.isAutomatedSender("Notifications@github.com")).isTrue();
        assertThat(ReplyCandidateFilter.isAutomatedSender("anna@acme.com")).isFalse();
        assertThat(ReplyCandidateFilter.isAutomatedSender(null)).isFalse();
    }

    @Test
    void a_personal_question_to_me_is_a_candidate() {
        assertThat(ReplyCandidateFilter.skip(personal(), NOW)).isEmpty();
    }

    @Test
    void my_own_message_is_skipped() {
        assertThat(ReplyCandidateFilter.skip(candidate("ME@work.example", List.of("anna@acme.com", "me@example.com"), "Hi"), NOW))
                .contains(ReplyCandidateFilter.Skip.OWN_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"noreply@shop.com", "no-reply@github.com", "do-not-reply@bank.at", "donotreply@x.de",
            "mailer-daemon@mail.example", "notification@linkedin.com", "notifications@github.com", "bounces+123@sendgrid.net",
            "info-noreply@acme.com"})
    void automated_senders_are_skipped(String sender) {
        assertThat(ReplyCandidateFilter.skip(candidate(sender, List.of("me@example.com"), "Hello"), NOW))
                .contains(ReplyCandidateFilter.Skip.AUTOMATED_SENDER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"This is an automated message, please do not reply.", "Diese E-Mail wurde automatisch generiert.",
            "Bitte antworten Sie nicht auf diese E-Mail.", "Click here to unsubscribe."})
    void automated_texts_are_skipped(String body) {
        assertThat(ReplyCandidateFilter.skip(candidate("anna@acme.com", List.of("me@example.com"), body), NOW))
                .contains(ReplyCandidateFilter.Skip.AUTOMATED_TEXT);
    }

    @Test
    void mail_that_only_copies_me_is_skipped() {
        assertThat(ReplyCandidateFilter.skip(candidate("anna@acme.com", List.of("team@acme.com"), "FYI"), NOW))
                .contains(ReplyCandidateFilter.Skip.NOT_ADDRESSED);
        assertThat(ReplyCandidateFilter.skip(candidate("anna@acme.com", List.of(), "FYI"), NOW))
                .contains(ReplyCandidateFilter.Skip.NOT_ADDRESSED);
    }

    @Test
    void an_answered_email_is_skipped() {
        var answered = new ReplyCandidateFilter.Candidate("anna@acme.com", List.of("me@example.com"), "Question?",
                NOW.minus(Duration.ofDays(1)), MINE, true, false);

        assertThat(ReplyCandidateFilter.skip(answered, NOW)).contains(ReplyCandidateFilter.Skip.ANSWERED);
    }

    @Test
    void an_email_followed_by_a_newer_one_in_its_conversation_is_skipped() {
        var overtaken = new ReplyCandidateFilter.Candidate("anna@acme.com", List.of("me@example.com"), "Question?",
                NOW.minus(Duration.ofDays(1)), MINE, false, true);

        assertThat(ReplyCandidateFilter.skip(overtaken, NOW)).contains(ReplyCandidateFilter.Skip.NEWER_MESSAGE);
    }

    @Test
    void emails_older_than_the_window_are_skipped() {
        var old = new ReplyCandidateFilter.Candidate("anna@acme.com", List.of("me@example.com"), "Question?",
                NOW.minus(ReplyCandidateFilter.WINDOW).minusSeconds(1), MINE, false, false);
        var recent = new ReplyCandidateFilter.Candidate("anna@acme.com", List.of("me@example.com"), "Question?",
                NOW.minus(ReplyCandidateFilter.WINDOW).plusSeconds(1), MINE, false, false);

        assertThat(ReplyCandidateFilter.skip(old, NOW)).contains(ReplyCandidateFilter.Skip.TOO_OLD);
        assertThat(ReplyCandidateFilter.skip(recent, NOW)).isEmpty();
    }

    @Test
    void a_missing_sender_or_body_does_not_break_the_rules() {
        assertThat(ReplyCandidateFilter.skip(candidate(null, List.of("me@example.com"), null), NOW))
                .contains(ReplyCandidateFilter.Skip.AUTOMATED_SENDER);
    }
}
