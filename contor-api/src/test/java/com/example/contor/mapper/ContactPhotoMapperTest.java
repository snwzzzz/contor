package com.example.contor.mapper;

import com.example.contor.domain.ContactPhoto;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ContactPhotoMapperTest {

    @Test
    void maps_contact_photo_without_the_image() {
        var photo = ContactPhoto.builder().id(new ContactPhoto.ContactPhotoId(9L)).emailAddress("bob@example.com")
                .image(new byte[] {1}).contentType("image/jpeg").build();

        var dto = ContactPhotoMapper.toDto(photo);

        assertEquals(9L, dto.getId());
        assertEquals("bob@example.com", dto.getEmailAddress());
    }

    @Test
    void handles_null_input_and_id() {
        assertNull(ContactPhotoMapper.toDto(null));
        assertNull(ContactPhotoMapper.toDto(ContactPhoto.builder().emailAddress("bob@example.com").build()).getId());
    }
}
