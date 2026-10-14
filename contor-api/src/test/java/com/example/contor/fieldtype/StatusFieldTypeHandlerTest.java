package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import java.util.List;
import java.util.Map;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StatusFieldTypeHandlerTest {

    private final StatusFieldTypeHandler handler = new StatusFieldTypeHandler();
    private final Map<String, Object> options = Map.of(
            "states", List.of("ORDERED", "SHIPPED", "DELIVERED"),
            "transitions", Map.of("ORDERED", List.of("SHIPPED"), "SHIPPED", List.of("DELIVERED")));

    @Test
    void accepts_only_configured_states() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.STATUS);
        assertThat(handler.columns(field(FieldType.STATUS, options))).containsExactly(ColumnSpec.of("f", SQLDataType.CLOB));
        assertThatCode(() -> handler.validate(field(FieldType.STATUS, options), "SHIPPED")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.STATUS, options), "LOST")).hasMessage("must be one of [ORDERED, SHIPPED, DELIVERED]");
    }

    @Test
    void new_records_start_in_the_first_state() {
        assertThat(handler.defaultValue(field(FieldType.STATUS, options))).isEqualTo("ORDERED");
    }

    @Test
    void enforces_allowed_transitions() {
        var field = field(FieldType.STATUS, options);
        assertThatCode(() -> handler.validateChange(field, "ORDERED", "SHIPPED")).doesNotThrowAnyException();
        assertThatCode(() -> handler.validateChange(field, "SHIPPED", "SHIPPED")).doesNotThrowAnyException();
        assertThatCode(() -> handler.validateChange(field, null, "DELIVERED")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validateChange(field, "ORDERED", "DELIVERED"))
                .hasMessage("transition from 'ORDERED' to 'DELIVERED' is not allowed");
        assertThatThrownBy(() -> handler.validateChange(field, "DELIVERED", "ORDERED"))
                .hasMessage("transition from 'DELIVERED' to 'ORDERED' is not allowed");
    }

    @Test
    void allows_any_change_without_transitions() {
        var free = field(FieldType.STATUS, Map.of("states", List.of("A", "B")));
        assertThatCode(() -> handler.validateChange(free, "B", "A")).doesNotThrowAnyException();
    }

    @Test
    void validates_states_and_transitions_options() {
        assertThatCode(() -> handler.validateOptions(options)).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validateOptions(Map.of())).hasMessage("STATUS options need a non-empty list 'states'");
        assertThatThrownBy(() -> handler.validateOptions(Map.of("states", List.of("A"), "transitions", Map.of("A", List.of("X")))))
                .hasMessage("transitions may only reference states");
        assertThatThrownBy(() -> handler.validateOptions(Map.of("states", List.of("A"), "transitions", List.of("A"))))
                .hasMessage("transitions must map a state to a list of states");
    }
}
