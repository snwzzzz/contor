package com.example.contor.exception;

public class ContactPhotoNotFoundException extends ResourceNotFoundException {
    public ContactPhotoNotFoundException(Long id) {
        super("Contact photo not found with id: " + id);
    }
}
