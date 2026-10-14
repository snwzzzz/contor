package com.example.contor.mapper;

import com.example.contor.api.model.ContactPhotoDto;
import com.example.contor.domain.ContactPhoto;

/** Contact photos; the image itself is served separately. */
public class ContactPhotoMapper {

    public static ContactPhotoDto toDto(ContactPhoto photo) {
        if (photo == null) return null;

        return new ContactPhotoDto()
                .id(photo.getId() != null ? photo.getId().id() : null)
                .emailAddress(photo.getEmailAddress());
    }
}
