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
class BooleanFieldTypeHandlerTest {

    private final BooleanFieldTypeHandler handler = new BooleanFieldTypeHandler();

    @Test
    void handles_booleans() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.BOOLEAN);
        assertThat(handler.columns(field(FieldType.BOOLEAN))).containsExactly(ColumnSpec.of("f", SQLDataType.BOOLEAN));
        assertThatCode(() -> handler.validate(field(FieldType.BOOLEAN), true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.BOOLEAN), "true")).hasMessage("must be true or false");
    }

    @Test
    void maps_to_and_from_db_and_parses_filters_strictly() {
        assertThat(handler.toDb(field(FieldType.BOOLEAN), false)).containsExactly(entry("f", false));
        assertThat(handler.fromDb(field(FieldType.BOOLEAN), record("f", true))).isEqualTo(true);
        assertThat(handler.parseFilterValue(field(FieldType.BOOLEAN), "true")).isEqualTo(true);
        assertThatThrownBy(() -> handler.parseFilterValue(field(FieldType.BOOLEAN), "yes")).hasMessage("must be true or false");
    }
}
