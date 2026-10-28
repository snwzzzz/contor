package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ApiFieldMappingTest {

    @Test
    public void can_map_a_json_path_to_a_field() {
        var mapping = new ApiFieldMapping("total_amount", "$.totals.gross");
        assertThat(mapping.fieldSlug()).isEqualTo("total_amount");
        assertThat(mapping.path()).isEqualTo("$.totals.gross");
    }

    @Test
    public void invalid_slugs_and_paths_throw_error() {
        assertThatThrownBy(() -> new ApiFieldMapping(null, "$.id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiFieldMapping("Total Amount", "$.id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiFieldMapping("total", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiFieldMapping("total", "$..gross")).isInstanceOf(IllegalArgumentException.class);
    }
}
