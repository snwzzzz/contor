package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import java.util.List;
import java.util.Map;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static com.example.contor.fieldtype.FieldFixtures.record;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EnumFieldTypeHandlerTest {

    private final EnumFieldTypeHandler handler = new EnumFieldTypeHandler();
    private final Map<String, Object> options = Map.of("values", List.of("DHL", "UPS"));

    @Test
    void accepts_only_configured_values() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.ENUM);
        assertThat(handler.columns(field(FieldType.ENUM, options))).containsExactly(ColumnSpec.of("f", SQLDataType.CLOB));
        assertThatCode(() -> handler.validate(field(FieldType.ENUM, options), "UPS")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.ENUM, options), "FedEx")).hasMessage("must be one of [DHL, UPS]");
        assertThatThrownBy(() -> handler.validate(field(FieldType.ENUM, options), 1)).hasMessage("must be one of [DHL, UPS]");
    }

    @Test
    void requires_a_non_empty_list_of_distinct_values() {
        assertThatCode(() -> handler.validateOptions(options)).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validateOptions(Map.of())).hasMessage("ENUM options need a non-empty list 'values'");
        assertThatThrownBy(() -> handler.validateOptions(Map.of("values", List.of()))).hasMessage("ENUM options need a non-empty list 'values'");
        assertThatThrownBy(() -> handler.validateOptions(Map.of("values", List.of("A", "A")))).hasMessage("values must be distinct, non-blank strings");
        assertThatThrownBy(() -> handler.validateOptions(Map.of("values", List.of("A", 2)))).hasMessage("values must be distinct, non-blank strings");
    }

    @Test
    void maps_to_and_from_db() {
        assertThat(handler.toDb(field(FieldType.ENUM, options), "DHL")).containsExactly(entry("f", "DHL"));
        assertThat(handler.fromDb(field(FieldType.ENUM, options), record("f", "DHL"))).isEqualTo("DHL");
    }
}
