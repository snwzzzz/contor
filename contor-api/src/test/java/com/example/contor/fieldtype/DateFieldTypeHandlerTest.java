package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import java.time.LocalDate;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static com.example.contor.fieldtype.FieldFixtures.record;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DateFieldTypeHandlerTest {

    private final DateFieldTypeHandler handler = new DateFieldTypeHandler();

    @Test
    void handles_iso_dates() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.DATE);
        assertThat(handler.columns(field(FieldType.DATE))).containsExactly(ColumnSpec.of("f", SQLDataType.LOCALDATE));
        assertThatCode(() -> handler.validate(field(FieldType.DATE), "2026-02-28")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.DATE), "2026-02-30")).hasMessage("must be a date (YYYY-MM-DD)");
        assertThatThrownBy(() -> handler.validate(field(FieldType.DATE), "28.02.2026")).hasMessage("must be a date (YYYY-MM-DD)");
        assertThatThrownBy(() -> handler.validate(field(FieldType.DATE), 20260228)).hasMessage("must be a date (YYYY-MM-DD)");
    }

    @Test
    void maps_to_and_from_db() {
        assertThat(handler.toDb(field(FieldType.DATE), "2026-02-28")).containsExactly(entry("f", LocalDate.of(2026, 2, 28)));
        assertThat(handler.fromDb(field(FieldType.DATE), record("f", LocalDate.of(2026, 2, 28)))).isEqualTo("2026-02-28");
        assertThat(handler.parseFilterValue(field(FieldType.DATE), "2026-02-28")).isEqualTo(LocalDate.of(2026, 2, 28));
    }
}
