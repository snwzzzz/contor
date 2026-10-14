package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class UsernameTest {

    @Test
    public void can_create_valid_username() {
        assertThat(new Username("dani.s_01-x").value()).isEqualTo("dani.s_01-x");
    }

    @Test
    public void null_and_empty_username_throws_error() {
        assertThatThrownBy(() -> new Username(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Username("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void invalid_username_throws_error() {
        assertThatThrownBy(() -> new Username("ab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username is not valid");
        assertThatThrownBy(() -> new Username("a".repeat(33))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Username("has space")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Username("x@y")).isInstanceOf(IllegalArgumentException.class);
    }
}
