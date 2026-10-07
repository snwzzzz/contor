package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class FileFieldTypeHandlerTest {

    private final FileFieldTypeHandler handler = new FileFieldTypeHandler();

    @Test
    void stores_a_file_reference_only() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.FILE);
        assertThat(handler.columns(field(FieldType.FILE))).containsExactly(ColumnSpec.of("f", SQLDataType.CLOB));
        assertThatCode(() -> handler.validate(field(FieldType.FILE), "files/2026/invoice.pdf")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.FILE), "x".repeat(2049))).hasMessage("must be at most 2048 characters");
        assertThatThrownBy(() -> handler.validate(field(FieldType.FILE), 3)).hasMessage("must be a string");
    }
}
