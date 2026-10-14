package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class FieldTypeRegistryTest {

    static List<FieldTypeHandler> allHandlers() {
        return List.of(new TextFieldTypeHandler(), new LongTextFieldTypeHandler(), new NumberFieldTypeHandler(),
                new MoneyFieldTypeHandler(), new DateFieldTypeHandler(), new DateTimeFieldTypeHandler(),
                new BooleanFieldTypeHandler(), new EnumFieldTypeHandler(), new StatusFieldTypeHandler(),
                new UrlFieldTypeHandler(), new EmailFieldTypeHandler(), new PhoneFieldTypeHandler(),
                new RelationFieldTypeHandler(), new FileFieldTypeHandler());
    }

    @Test
    void can_resolve_handler_by_type() {
        var money = new MoneyFieldTypeHandler();
        var registry = new FieldTypeRegistry(List.of(new TextFieldTypeHandler(), money));

        assertThat(registry.handler(FieldType.MONEY)).isSameAs(money);
    }

    @Test
    void every_field_type_has_exactly_one_handler() {
        var registry = new FieldTypeRegistry(allHandlers());

        Arrays.stream(FieldType.values()).forEach(type -> assertThat(registry.handler(type).fieldType()).isEqualTo(type));
    }

    @Test
    void rejects_two_handlers_for_one_type() {
        assertThatThrownBy(() -> new FieldTypeRegistry(List.of(new TextFieldTypeHandler(), new TextFieldTypeHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEXT");
    }

    @Test
    void reports_missing_handlers() {
        var registry = new FieldTypeRegistry(List.of(new TextFieldTypeHandler()));

        assertThatThrownBy(() -> registry.handler(FieldType.DATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No FieldTypeHandler for DATE");
    }
}
