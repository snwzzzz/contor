package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class HexColorTest {

    @Test
    public void can_create_valid_color() {
        var color = new HexColor("#1A2b3C");
        assertThat(color.value()).isEqualTo("#1A2b3C");
    }

    @Test
    public void null_and_empty_color_throws_error() {
        assertThatThrownBy(() -> new HexColor(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HexColor("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void invalid_color_throws_error() {
        assertThatThrownBy(() -> new HexColor("red"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("color is not valid");
    }
}
