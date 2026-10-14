package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmailFieldTypeHandlerTest {

    private final EmailFieldTypeHandler handler = new EmailFieldTypeHandler();

    @Test
    void accepts_email_addresses() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.EMAIL);
        assertThat(handler.columns(field(FieldType.EMAIL))).containsExactly(ColumnSpec.of("f", SQLDataType.CLOB));
        assertThatCode(() -> handler.validate(field(FieldType.EMAIL), "jane@example.com")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.EMAIL), "jane")).hasMessage("must be an email address");
        assertThatThrownBy(() -> handler.validate(field(FieldType.EMAIL), 5)).hasMessage("must be an email address");
    }
}
