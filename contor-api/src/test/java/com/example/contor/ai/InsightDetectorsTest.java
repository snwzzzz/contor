package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InsightDetectorsTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    /** Monday 12 October 2026, 10:00 in Berlin. */
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");

    private static InsightDetectors.SentMail sent(String body, String sentAt) {
        return new InsightDetectors.SentMail(42L, "Anna Berger", "anna@acme.com", "Workshop dates", body, Instant.parse(sentAt));
    }

    @Test
    void a_question_without_an_answer_for_days_becomes_a_follow_up() {
        Insight insight = InsightDetectors.unansweredQuestion(sent("Hi Anna, which day works for you?", "2026-10-05T09:00:00Z"), NOW, ZONE)
                .orElseThrow();

        assertThat(insight.key()).isEqualTo("unanswered:42");
        assertThat(insight.title()).isEqualTo("No answer from Anna Berger yet");
        assertThat(insight.observation()).contains("Workshop dates").contains("7 days");
        assertThat(insight.evidenceEmailIds()).containsExactly(42L);
        assertThat(insight.followUps()).containsExactly(new Insight.FollowUp("tasks", "Follow up with Anna Berger: Workshop dates",
                Map.of("due_date", "2026-10-13")));
        assertThat(insight.source()).isEqualTo("rule:unanswered-question");
    }

    @Test
    void a_mail_without_a_question_or_too_recent_is_no_insight() {
        assertThat(InsightDetectors.unansweredQuestion(sent("Here are the slides.", "2026-10-05T09:00:00Z"), NOW, ZONE)).isEmpty();
        assertThat(InsightDetectors.unansweredQuestion(sent("Which day works?", "2026-10-10T09:00:00Z"), NOW, ZONE)).isEmpty();
    }

    @Test
    void overlapping_appointments_are_a_clash_to_resolve_the_day_before() {
        var interview = new InsightDetectors.Appointment(7L, "Interview at Acme", Instant.parse("2026-10-15T08:00:00Z"),
                Instant.parse("2026-10-15T09:30:00Z"), List.of(100L));
        var dentist = new InsightDetectors.Appointment(9L, "Dentist", Instant.parse("2026-10-15T09:00:00Z"),
                Instant.parse("2026-10-15T10:00:00Z"), List.of(101L));
        var later = new InsightDetectors.Appointment(11L, "Lunch", Instant.parse("2026-10-15T11:00:00Z"),
                Instant.parse("2026-10-15T12:00:00Z"), List.of());

        List<Insight> clashes = InsightDetectors.clashes(List.of(dentist, interview, later), ZONE);

        assertThat(clashes).singleElement().satisfies(clash -> {
            assertThat(clash.key()).isEqualTo("clash:7:9");
            assertThat(clash.title()).isEqualTo("Two appointments overlap on Thursday, 15 October");
            assertThat(clash.observation()).contains("Interview at Acme").contains("Dentist");
            assertThat(clash.evidenceEmailIds()).containsExactly(100L, 101L);
            assertThat(clash.followUps()).containsExactly(new Insight.FollowUp("tasks",
                    "Resolve the clash: Interview at Acme / Dentist", Map.of("due_date", "2026-10-14")));
        });
    }
}
