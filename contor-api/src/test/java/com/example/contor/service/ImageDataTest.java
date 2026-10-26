package com.example.contor.service;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ImageDataTest {

    @Test
    void content_type_is_detected_from_the_bytes() {
        assertThat(ImageData.contentTypeOf(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D})).isEqualTo("image/png");
        assertThat(ImageData.contentTypeOf(new byte[] {'G', 'I', 'F', '8', '9'})).isEqualTo("image/gif");
        assertThat(ImageData.contentTypeOf(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'})).isEqualTo("image/webp");
        assertThat(ImageData.contentTypeOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).isEqualTo("image/jpeg");
        assertThat(ImageData.contentTypeOf(new byte[] {1, 2})).isEqualTo("image/jpeg");
    }
}
