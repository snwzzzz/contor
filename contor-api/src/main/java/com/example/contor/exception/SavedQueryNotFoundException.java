package com.example.contor.exception;

public class SavedQueryNotFoundException extends ResourceNotFoundException {
    public SavedQueryNotFoundException(Long id) {
        super("Saved query not found with id: " + id);
    }
}
