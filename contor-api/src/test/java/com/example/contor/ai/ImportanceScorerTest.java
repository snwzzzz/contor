package com.example.contor.ai;

import com.example.contor.domain.ProposalImportance;
import com.example.contor.domain.ProposalKind;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ImportanceScorerTest {

    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private static ImportanceScorer.Importance score(ProposalKind kind, String collection, Map<String, Object> payload,
                                                     ImportanceSignals signals) {
        return ImportanceScorer.score(kind, collection, payload, signals, NOW, ZONE);
    }

    private static Map<String, Object> values(String field, String value) {
        return Map.of("values", Map.of(field, value));
    }

    @Test
    void levels_follow_fixed_score_thresholds() {
        assertThat(ProposalImportance.of(100)).isEqualTo(ProposalImportance.URGENT);
        assertThat(ProposalImportance.of(80)).isEqualTo(ProposalImportance.URGENT);
        assertThat(ProposalImportance.of(79)).isEqualTo(ProposalImportance.HIGH);
        assertThat(ProposalImportance.of(60)).isEqualTo(ProposalImportance.HIGH);
        assertThat(ProposalImportance.of(59)).isEqualTo(ProposalImportance.NORMAL);
        assertThat(ProposalImportance.of(35)).isEqualTo(ProposalImportance.NORMAL);
        assertThat(ProposalImportance.of(34)).isEqualTo(ProposalImportance.LOW);
        assertThat(ProposalImportance.HIGH.minScore()).isEqualTo(60);
        assertThat(ProposalImportance.LOW.minScore()).isZero();
    }

    @Test
    void the_kind_sets_the_base_a_reply_above_a_to_do_above_an_appointment_above_a_finding_above_a_relation() {
        int reply = score(ProposalKind.DRAFT_REPLY, null, Map.of(), ImportanceSignals.NONE).score();
        int task = score(ProposalKind.CREATE_RECORD, "tasks", Map.of(), ImportanceSignals.NONE).score();
        int event = score(ProposalKind.CREATE_RECORD, "events", Map.of(), ImportanceSignals.NONE).score();
        int finding = score(ProposalKind.CREATE_RECORD, "orders", Map.of(), ImportanceSignals.NONE).score();
        int relation = score(ProposalKind.CREATE_RELATION, null, Map.of(), ImportanceSignals.NONE).score();

        assertThat(reply).isGreaterThan(task);
        assertThat(task).isGreaterThan(event);
        assertThat(event).isGreaterThan(finding);
        assertThat(finding).isGreaterThan(relation);
        assertThat(ProposalImportance.of(finding)).isEqualTo(ProposalImportance.LOW);
        assertThat(ProposalImportance.of(task)).isEqualTo(ProposalImportance.NORMAL);
    }

    @Test
    void a_to_do_due_tomorrow_is_high_and_says_why() {
        ImportanceScorer.Importance importance = score(ProposalKind.CREATE_RECORD, "tasks", values("due_date", "2026-10-13"),
                ImportanceSignals.NONE);

        assertThat(importance.level()).isEqualTo(ProposalImportance.HIGH);
        assertThat(importance.reason()).contains("to-do").contains("within 2 days");
    }

    @Test
    void an_appointment_next_week_counts_less_than_one_in_two_days_and_a_past_one_least() {
        int soon = score(ProposalKind.CREATE_RECORD, "events", values("starts_at", "2026-10-13T09:00:00+02:00"), ImportanceSignals.NONE).score();
        int week = score(ProposalKind.CREATE_RECORD, "events", values("starts_at", "2026-10-18T09:00:00+02:00"), ImportanceSignals.NONE).score();
        int later = score(ProposalKind.CREATE_RECORD, "events", values("starts_at", "2026-11-30T09:00:00+01:00"), ImportanceSignals.NONE).score();
        int past = score(ProposalKind.CREATE_RECORD, "events", values("starts_at", "2026-09-01T09:00:00+02:00"), ImportanceSignals.NONE).score();

        assertThat(soon).isGreaterThan(week);
        assertThat(week).isGreaterThan(later);
        assertThat(later).isGreaterThan(past);
    }

    @Test
    void an_urgent_reply_to_a_known_contact_is_urgent() {
        ImportanceScorer.Importance importance = score(ProposalKind.DRAFT_REPLY, null, Map.of(),
                new ImportanceSignals("high", true, false, false));

        assertThat(importance.level()).isEqualTo(ProposalImportance.URGENT);
        assertThat(importance.reason()).contains("reply").contains("urgent").contains("contact");
    }

    @Test
    void someone_you_write_with_raises_and_a_bulk_sender_lowers_the_score() {
        int plain = score(ProposalKind.CREATE_RECORD, "tasks", Map.of(), ImportanceSignals.NONE).score();

        assertThat(score(ProposalKind.CREATE_RECORD, "tasks", Map.of(), new ImportanceSignals(null, false, true, false)).score())
                .isGreaterThan(plain);
        assertThat(score(ProposalKind.CREATE_RECORD, "tasks", Map.of(), new ImportanceSignals(null, false, false, true)).score())
                .isLessThan(plain);
        assertThat(score(ProposalKind.DRAFT_REPLY, null, Map.of(), new ImportanceSignals("low", false, false, false)).score())
                .isLessThan(score(ProposalKind.DRAFT_REPLY, null, Map.of(), ImportanceSignals.NONE).score());
    }

    @Test
    void the_score_stays_between_0_and_100_and_unreadable_dates_are_ignored() {
        ImportanceSignals everything = new ImportanceSignals("high", true, true, false);
        ImportanceSignals nothing = new ImportanceSignals("low", false, false, true);

        assertThat(score(ProposalKind.CREATE_RECORD, "tasks", values("due_date", "2026-10-12"), everything).score()).isEqualTo(100);
        assertThat(score(ProposalKind.CREATE_RELATION, null, Map.of(), nothing).score()).isZero();
        assertThat(score(ProposalKind.CREATE_RECORD, "tasks", values("due_date", "next Friday"), ImportanceSignals.NONE).score())
                .isEqualTo(score(ProposalKind.CREATE_RECORD, "tasks", Map.of(), ImportanceSignals.NONE).score());
    }
}
