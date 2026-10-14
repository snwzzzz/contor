package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.util.Map;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static com.example.contor.fieldtype.FieldFixtures.record;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NumberFieldTypeHandlerTest {

    private final NumberFieldTypeHandler handler = new NumberFieldTypeHandler();

    @Test
    void handles_numbers_as_numeric_column() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.NUMBER);
        assertThat(handler.columns(field(FieldType.NUMBER))).containsExactly(ColumnSpec.of("f", SQLDataType.NUMERIC));
    }

    @Test
    void accepts_integers_and_decimals_within_bounds() {
        var bounded = field(FieldType.NUMBER, Map.of("min", 0, "max", 10));
        assertThatCode(() -> handler.validate(bounded, 3)).doesNotThrowAnyException();
        assertThatCode(() -> handler.validate(bounded, 9.75)).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(bounded, -1)).hasMessage("must be at least 0");
        assertThatThrownBy(() -> handler.validate(bounded, 10.5)).hasMessage("must be at most 10");
    }

    @Test
    void rejects_non_numbers() {
        assertThatThrownBy(() -> handler.validate(field(FieldType.NUMBER), "3")).hasMessage("must be a number");
        assertThatThrownBy(() -> handler.validate(field(FieldType.NUMBER), Double.NaN)).hasMessage("must be a number");
    }

    @Test
    void validates_bound_options() {
        assertThatThrownBy(() -> handler.validateOptions(Map.of("min", "zero"))).hasMessage("min must be a number");
        assertThatThrownBy(() -> handler.validateOptions(Map.of("min", 5, "max", 1))).hasMessage("min must not be greater than max");
    }

    @Test
    void maps_to_and_from_db_without_trailing_zeros() {
        assertThat(handler.toDb(field(FieldType.NUMBER), 2.5)).containsExactly(entry("f", new BigDecimal("2.5")));
        assertThat(handler.fromDb(field(FieldType.NUMBER), record("f", new BigDecimal("2.5000")))).isEqualTo(new BigDecimal("2.5"));
        assertThat(handler.fromDb(field(FieldType.NUMBER), record("f", new BigDecimal("100")))).hasToString("100");
        assertThat(handler.fromDb(field(FieldType.NUMBER), record("f", null))).isNull();
        assertThat(handler.parseFilterValue(field(FieldType.NUMBER), "7.5")).isEqualTo(new BigDecimal("7.5"));
        assertThatThrownBy(() -> handler.parseFilterValue(field(FieldType.NUMBER), "abc")).hasMessage("must be a number");
    }
}
