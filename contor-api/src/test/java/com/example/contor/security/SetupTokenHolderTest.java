package com.example.contor.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SetupTokenHolderTest {

    private final SetupTokenHolder holder = new SetupTokenHolder();

    @Test
    void issues_a_long_random_url_safe_token() {
        var first = holder.issue();
        var second = holder.issue();

        assertThat(first).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void matches_only_the_current_token() {
        var token = holder.issue();

        assertThat(holder.matches(token)).isTrue();
        assertThat(holder.matches("wrong")).isFalse();
        assertThat(holder.matches(null)).isFalse();
    }

    @Test
    void nothing_matches_before_a_token_was_issued() {
        assertThat(holder.matches("anything")).isFalse();
        assertThat(holder.consume("anything")).isFalse();
    }

    @Test
    void a_token_can_be_consumed_exactly_once() {
        var token = holder.issue();

        assertThat(holder.consume(token)).isTrue();
        assertThat(holder.consume(token)).isFalse();
        assertThat(holder.matches(token)).isFalse();
    }
}
