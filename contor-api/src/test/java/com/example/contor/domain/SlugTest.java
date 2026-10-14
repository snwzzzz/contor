package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SlugTest {

    @Test
    public void can_create_valid_slug() {
        assertThat(new Slug("job_applications_2").value()).isEqualTo("job_applications_2");
        assertThat(new Slug("ab").value()).isEqualTo("ab");
        assertThat(new Slug("a" + "b".repeat(40)).value()).hasSize(41);
    }

    @Test
    public void null_and_empty_slug_throws_error() {
        assertThatThrownBy(() -> new Slug(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Slug("")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "1abc", "_abc", "Deliveries", "has-dash", "has space", "c_x\"; drop table app_user; --", "ä_umlaut"})
    public void invalid_slug_throws_error(String value) {
        assertThatThrownBy(() -> new Slug(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("slug is not valid");
    }

    @Test
    public void too_long_slug_throws_error() {
        assertThatThrownBy(() -> new Slug("a" + "b".repeat(41))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"select", "table", "user", "order", "where", "primary", "oid", "ctid", "xmin"})
    public void reserved_word_throws_error(String value) {
        assertThatThrownBy(() -> new Slug(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("slug is a reserved word: " + value);
    }
}
