package com.example.contor.exception;

public class RelationNotFoundException extends ResourceNotFoundException {
    public RelationNotFoundException(Long id) {
        super("Relation not found with id: " + id);
    }
}
