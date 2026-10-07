package com.example.contor.exception;

public class AppUserNotFoundException extends ResourceNotFoundException {
    public AppUserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }
}
