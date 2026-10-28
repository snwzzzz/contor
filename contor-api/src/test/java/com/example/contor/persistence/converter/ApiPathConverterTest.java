package com.example.contor.persistence.converter;

import com.example.contor.domain.ApiPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ApiPathConverterTest {

    private ApiPathConverter converter;

    @BeforeEach
    void setup() {
        this.converter = new ApiPathConverter();
    }

    @Test
    public void can_map_to_db() {
        var value = new ApiPath("/v2/orders");
        assertThat(converter.convertToDatabaseColumn(value)).isEqualTo(value.value());
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    public void can_map_from_db() {
        var value = new ApiPath("/v2/orders");
        assertThat(converter.convertToEntityAttribute(value.value())).isEqualTo(value);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
