package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import java.util.Map;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static com.example.contor.fieldtype.FieldFixtures.record;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TextFieldTypeHandlerTest {

    private final TextFieldTypeHandler handler = new TextFieldTypeHandler();

    @Test
    void handles_text() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.TEXT);
        assertThat(handler.columns(field(FieldType.TEXT))).containsExactly(ColumnSpec.of("f", SQLDataType.CLOB));
    }

    @Test
    void accepts_strings_up_to_the_max_length() {
        assertThatCode(() -> handler.validate(field(FieldType.TEXT), "DHL")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.TEXT), "x".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("must be at most 1000 characters");
        assertThatThrownBy(() -> handler.validate(field(FieldType.TEXT, Map.of("maxLength", 3)), "abcd"))
                .hasMessage("must be at most 3 characters");
    }

    @Test
    void rejects_non_strings() {
        assertThatThrownBy(() -> handler.validate(field(FieldType.TEXT), 42))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("must be a string");
    }

    @Test
    void validates_max_length_option() {
        assertThatCode(() -> handler.validateOptions(Map.of("maxLength", 50))).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validateOptions(Map.of("maxLength", -1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("maxLength must be a positive integer");
    }

    @Test
    void maps_to_and_from_db() {
        assertThat(handler.toDb(field(FieldType.TEXT), "DHL")).containsExactly(entry("f", "DHL"));
        assertThat(handler.toDb(field(FieldType.TEXT), null)).containsEntry("f", null);
        assertThat(handler.fromDb(field(FieldType.TEXT), record("f", "DHL"))).isEqualTo("DHL");
        assertThat(handler.parseFilterValue(field(FieldType.TEXT), "DHL")).isEqualTo("DHL");
    }
}
