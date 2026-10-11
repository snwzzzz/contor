package com.example.contor.ai.eval;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReplyTriageScoreTest {

    @Test
    void counts_decisions_into_precision_and_recall() {
        ReplyTriageScore score = ReplyTriageScore.empty()
                .add(true, true)
                .add(true, true)
                .add(true, false)
                .add(false, true)
                .add(false, false);

        assertThat(score).isEqualTo(new ReplyTriageScore(2, 1, 1, 1));
        assertThat(score.precision()).isEqualTo(2.0 / 3);
        assertThat(score.recall()).isEqualTo(2.0 / 3);
    }

    @Test
    void nothing_predicted_or_expected_is_perfect() {
        assertThat(ReplyTriageScore.empty().precision()).isEqualTo(1.0);
        assertThat(ReplyTriageScore.empty().add(false, false).recall()).isEqualTo(1.0);
    }
}
