package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static com.example.contor.fieldtype.FieldFixtures.record;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LongTextFieldTypeHandlerTest {

    private final LongTextFieldTypeHandler handler = new LongTextFieldTypeHandler();

    @Test
    void handles_long_text_up_to_100k_characters() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.LONG_TEXT);
        assertThat(handler.columns(field(FieldType.LONG_TEXT))).containsExactly(ColumnSpec.of("f", SQLDataType.CLOB));
        assertThatCode(() -> handler.validate(field(FieldType.LONG_TEXT), "x".repeat(100_000))).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.LONG_TEXT), "x".repeat(100_001)))
                .hasMessage("must be at most 100000 characters");
        assertThatThrownBy(() -> handler.validate(field(FieldType.LONG_TEXT), true)).hasMessage("must be a string");
    }

    @Test
    void maps_to_and_from_db() {
        assertThat(handler.toDb(field(FieldType.LONG_TEXT), "notes")).containsExactly(entry("f", "notes"));
        assertThat(handler.fromDb(field(FieldType.LONG_TEXT), record("f", "notes"))).isEqualTo("notes");
    }
}
