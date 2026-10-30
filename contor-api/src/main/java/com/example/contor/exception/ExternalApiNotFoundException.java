package com.example.contor.exception;

/** Also thrown for another user's app: apps are private. */
public class ExternalApiNotFoundException extends ResourceNotFoundException {

    public ExternalApiNotFoundException(Long id) {
        super("App not found with id: " + id);
    }
}
