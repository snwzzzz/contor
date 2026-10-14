package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static com.example.contor.fieldtype.FieldFixtures.record;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MoneyFieldTypeHandlerTest {

    private final MoneyFieldTypeHandler handler = new MoneyFieldTypeHandler();

    @Test
    void uses_an_amount_and_a_currency_column() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.MONEY);
        assertThat(handler.columns(field(FieldType.MONEY))).containsExactly(
                ColumnSpec.of("f_amount", SQLDataType.NUMERIC(19, 4)),
                ColumnSpec.of("f_currency", SQLDataType.CHAR(3)));
    }

    @Test
    void accepts_amount_with_iso_currency() {
        assertThatCode(() -> handler.validate(field(FieldType.MONEY), Map.of("amount", 19.99, "currency", "EUR"))).doesNotThrowAnyException();
    }

    @Test
    void rejects_invalid_money() {
        assertThatThrownBy(() -> handler.validate(field(FieldType.MONEY), 19.99)).hasMessage("must be an object with amount and currency");
        assertThatThrownBy(() -> handler.validate(field(FieldType.MONEY), Map.of("amount", "19", "currency", "EUR"))).hasMessage("amount must be a number");
        assertThatThrownBy(() -> handler.validate(field(FieldType.MONEY), Map.of("amount", 1.23456, "currency", "EUR"))).hasMessage("amount must have at most 4 decimal places");
        assertThatThrownBy(() -> handler.validate(field(FieldType.MONEY), Map.of("amount", 1, "currency", "eur"))).hasMessage("currency must be an ISO 4217 code");
        assertThatThrownBy(() -> handler.validate(field(FieldType.MONEY), Map.of("amount", 1, "currency", "XYZ"))).hasMessage("currency must be an ISO 4217 code");
        assertThatThrownBy(() -> handler.validate(field(FieldType.MONEY), Map.of("amount", 1))).hasMessage("currency must be an ISO 4217 code");
    }

    @Test
    void maps_to_and_from_db() {
        assertThat(handler.toDb(field(FieldType.MONEY), Map.of("amount", 19.99, "currency", "EUR")))
                .containsEntry("f_amount", new BigDecimal("19.99")).containsEntry("f_currency", "EUR");
        assertThat(handler.toDb(field(FieldType.MONEY), null)).containsEntry("f_amount", null).containsEntry("f_currency", null);

        var columns = new LinkedHashMap<String, Object>();
        columns.put("f_amount", new BigDecimal("19.9900"));
        columns.put("f_currency", "EUR");
        assertThat(handler.fromDb(field(FieldType.MONEY), record(columns)))
                .isEqualTo(Map.of("amount", new BigDecimal("19.99"), "currency", "EUR"));

        var empty = new LinkedHashMap<String, Object>();
        empty.put("f_amount", null);
        empty.put("f_currency", null);
        assertThat(handler.fromDb(field(FieldType.MONEY), record(empty))).isNull();
    }

    @Test
    void filters_on_the_amount() {
        assertThat(handler.parseFilterValue(field(FieldType.MONEY), "10")).isEqualTo(new BigDecimal("10"));
    }
}
