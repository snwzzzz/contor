package com.example.contor.exception;

public class RuleNotFoundException extends ResourceNotFoundException {
    public RuleNotFoundException(Long id) {
        super("Rule not found with id: " + id);
    }
}
