package com.example.contor.exception;

public class CollectionNotFoundException extends ResourceNotFoundException {
    public CollectionNotFoundException(Long id) {
        super("Collection not found with id: " + id);
    }
}
