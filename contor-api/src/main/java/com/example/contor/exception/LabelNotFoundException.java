package com.example.contor.exception;

public class LabelNotFoundException extends ResourceNotFoundException {
    public LabelNotFoundException(Long id) {
        super("Label not found with id: " + id);
    }
}
