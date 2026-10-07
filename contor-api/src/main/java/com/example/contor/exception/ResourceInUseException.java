package com.example.contor.exception;

/** A resource cannot be removed because other data still refers to it. */
public class ResourceInUseException extends ConflictException {
    public ResourceInUseException(String message) {
        super(message);
    }
}
