package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PhoneFieldTypeHandlerTest {

    private final PhoneFieldTypeHandler handler = new PhoneFieldTypeHandler();

    @Test
    void accepts_common_phone_formats() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.PHONE);
        assertThat(handler.columns(field(FieldType.PHONE))).containsExactly(ColumnSpec.of("f", SQLDataType.CLOB));
        assertThatCode(() -> handler.validate(field(FieldType.PHONE), "+49 (30) 123-456/7")).doesNotThrowAnyException();
        assertThatCode(() -> handler.validate(field(FieldType.PHONE), "0301234567")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.PHONE), "call me")).hasMessage("must be a phone number");
        assertThatThrownBy(() -> handler.validate(field(FieldType.PHONE), "+-")).hasMessage("must be a phone number");
        assertThatThrownBy(() -> handler.validate(field(FieldType.PHONE), 123456)).hasMessage("must be a phone number");
    }
}
