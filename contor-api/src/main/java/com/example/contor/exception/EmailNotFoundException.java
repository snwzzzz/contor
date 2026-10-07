package com.example.contor.exception;

public class EmailNotFoundException extends ResourceNotFoundException {
    public EmailNotFoundException(Long id) {
        super("Email not found with id: " + id);
    }
}
