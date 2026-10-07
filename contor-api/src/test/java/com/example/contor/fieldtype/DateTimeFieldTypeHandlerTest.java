package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static com.example.contor.fieldtype.FieldFixtures.record;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DateTimeFieldTypeHandlerTest {

    private final DateTimeFieldTypeHandler handler = new DateTimeFieldTypeHandler();

    @Test
    void handles_offset_date_times() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.DATETIME);
        assertThat(handler.columns(field(FieldType.DATETIME))).containsExactly(ColumnSpec.of("f", SQLDataType.TIMESTAMPWITHTIMEZONE));
        assertThatCode(() -> handler.validate(field(FieldType.DATETIME), "2026-02-28T10:15:00+02:00")).doesNotThrowAnyException();
        assertThatCode(() -> handler.validate(field(FieldType.DATETIME), "2026-02-28T10:15:00Z")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.DATETIME), "2026-02-28T10:15:00"))
                .hasMessage("must be an ISO-8601 date-time with offset");
    }

    @Test
    void stores_as_timestamptz_and_returns_utc() {
        var expected = OffsetDateTime.of(2026, 2, 28, 10, 15, 0, 0, ZoneOffset.ofHours(2));
        assertThat(handler.toDb(field(FieldType.DATETIME), "2026-02-28T10:15:00+02:00")).containsExactly(entry("f", expected));
        assertThat(handler.fromDb(field(FieldType.DATETIME), record("f", expected))).isEqualTo("2026-02-28T08:15:00Z");
    }
}
