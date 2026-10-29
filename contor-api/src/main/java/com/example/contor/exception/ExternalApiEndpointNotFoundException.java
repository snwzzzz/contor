package com.example.contor.exception;

/** Also thrown for an endpoint of another app. */
public class ExternalApiEndpointNotFoundException extends ResourceNotFoundException {

    public ExternalApiEndpointNotFoundException(Long id) {
        super("App endpoint not found with id: " + id);
    }
}
