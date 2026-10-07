package com.example.contor.exception;

public class RelationTypeNotFoundException extends ResourceNotFoundException {
    public RelationTypeNotFoundException(Long id) {
        super("Relation type not found with id: " + id);
    }
}
