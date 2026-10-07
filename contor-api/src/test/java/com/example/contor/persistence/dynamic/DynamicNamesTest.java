package com.example.contor.persistence.dynamic;

import com.example.contor.domain.Slug;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DynamicNamesTest {

    @Test
    void derives_table_names_from_slugs() {
        assertThat(DynamicNames.tableName(new Slug("deliveries"))).isEqualTo("c_deliveries");
    }

    @Test
    void keeps_short_constraint_names_readable() {
        assertThat(DynamicNames.constraintName("fk", "c_people", "employer")).isEqualTo("fk_c_people_employer");
    }

    @Test
    void shortens_long_constraint_names_to_63_characters_without_collisions() {
        var table = "c_" + "a".repeat(41);
        var first = DynamicNames.constraintName("ix", table, "b".repeat(41));
        var second = DynamicNames.constraintName("ix", table, "b".repeat(40) + "c");

        assertThat(first).hasSizeLessThanOrEqualTo(63).startsWith("ix_c_aaa");
        assertThat(second).hasSizeLessThanOrEqualTo(63);
        assertThat(first).isNotEqualTo(second);
    }
}
