package com.example.contor.exception;

public class CollectionFieldNotFoundException extends ResourceNotFoundException {
    public CollectionFieldNotFoundException(Long id) {
        super("Field not found with id: " + id);
    }
}
