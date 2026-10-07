package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class EmailTest {

    @Test
    public void can_create_valid_email() {
        assertThat(new Email("first.last+tag@sub.example.com").value()).isEqualTo("first.last+tag@sub.example.com");
    }

    @Test
    public void null_and_empty_email_throws_error() {
        assertThatThrownBy(() -> new Email(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void invalid_email_throws_error() {
        assertThatThrownBy(() -> new Email("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email is not valid");
        assertThatThrownBy(() -> new Email("a@b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("a b@example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("a".repeat(250) + "@example.com")).isInstanceOf(IllegalArgumentException.class);
    }
}
