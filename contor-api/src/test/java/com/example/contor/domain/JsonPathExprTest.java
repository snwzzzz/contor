package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class JsonPathExprTest {

    @Test
    public void can_create_the_supported_subset() {
        assertThat(new JsonPathExpr("$").value()).isEqualTo("$");
        assertThat(new JsonPathExpr("$.id").value()).isEqualTo("$.id");
        assertThat(new JsonPathExpr("$.data.orders[*]").value()).isEqualTo("$.data.orders[*]");
        assertThat(new JsonPathExpr("$[*].customer.first_name").value()).isEqualTo("$[*].customer.first_name");
        assertThat(new JsonPathExpr("$.items[0].sku-code").value()).isEqualTo("$.items[0].sku-code");
    }

    @Test
    public void null_and_empty_path_throws_error() {
        assertThatThrownBy(() -> new JsonPathExpr(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JsonPathExpr("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void filters_scripts_recursion_and_other_syntax_are_rejected() {
        assertThatThrownBy(() -> new JsonPathExpr("data.orders")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JsonPathExpr("$..id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JsonPathExpr("$.orders[?(@.total > 10)]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JsonPathExpr("$['orders']")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JsonPathExpr("$.orders[-1]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JsonPathExpr("$." + "a".repeat(200))).isInstanceOf(IllegalArgumentException.class);
    }
}
