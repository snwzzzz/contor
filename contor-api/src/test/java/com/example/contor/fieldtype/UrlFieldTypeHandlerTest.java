package com.example.contor.fieldtype;

import com.example.contor.domain.FieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jooq.impl.SQLDataType;

import static com.example.contor.fieldtype.FieldFixtures.field;
import static org.assertj.core.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UrlFieldTypeHandlerTest {

    private final UrlFieldTypeHandler handler = new UrlFieldTypeHandler();

    @Test
    void accepts_http_and_https_urls_only() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.URL);
        assertThat(handler.columns(field(FieldType.URL))).containsExactly(ColumnSpec.of("f", SQLDataType.CLOB));
        assertThatCode(() -> handler.validate(field(FieldType.URL), "https://example.com/track?id=1")).doesNotThrowAnyException();
        assertThatCode(() -> handler.validate(field(FieldType.URL), "http://localhost:8080")).doesNotThrowAnyException();
        assertThatThrownBy(() -> handler.validate(field(FieldType.URL), "javascript:alert(1)")).hasMessage("must be an http(s) URL");
        assertThatThrownBy(() -> handler.validate(field(FieldType.URL), "ftp://example.com")).hasMessage("must be an http(s) URL");
        assertThatThrownBy(() -> handler.validate(field(FieldType.URL), "not a url")).hasMessage("must be an http(s) URL");
        assertThatThrownBy(() -> handler.validate(field(FieldType.URL), "https://" + "a".repeat(2050))).hasMessage("must be an http(s) URL");
    }
}
