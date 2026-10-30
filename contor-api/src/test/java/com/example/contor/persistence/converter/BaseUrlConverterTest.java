package com.example.contor.persistence.converter;

import com.example.contor.domain.BaseUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class BaseUrlConverterTest {

    private BaseUrlConverter converter;

    @BeforeEach
    void setup() {
        this.converter = new BaseUrlConverter();
    }

    @Test
    public void can_map_to_db() {
        var value = new BaseUrl("https://api.shop.example/v1");
        assertThat(converter.convertToDatabaseColumn(value)).isEqualTo(value.value());
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    public void can_map_from_db() {
        var value = new BaseUrl("https://api.shop.example/v1");
        assertThat(converter.convertToEntityAttribute(value.value())).isEqualTo(value);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
