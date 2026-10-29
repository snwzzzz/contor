package com.example.contor.exception;

public class ApiDiscoveryNotFoundException extends ResourceNotFoundException {
    public ApiDiscoveryNotFoundException(String id) {
        super("Discovery not found with id: " + id);
    }
}
