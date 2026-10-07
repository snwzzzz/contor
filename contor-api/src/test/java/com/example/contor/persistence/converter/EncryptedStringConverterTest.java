package com.example.contor.persistence.converter;

import com.example.contor.config.EncryptionProperties;
import com.example.contor.security.FieldEncryptor;
import com.example.contor.security.FieldEncryptorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class EncryptedStringConverterTest {

    private EncryptedStringConverter converter;

    @BeforeEach
    void setup() {
        this.converter = new EncryptedStringConverter(new FieldEncryptor(new EncryptionProperties(FieldEncryptorTest.TEST_KEY, 1)));
    }

    @Test
    public void can_map_to_db() {
        assertThat(converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP"))
                .startsWith("v1:")
                .doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    public void can_map_from_db() {
        var stored = converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
