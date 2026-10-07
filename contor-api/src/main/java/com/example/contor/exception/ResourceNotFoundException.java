package com.example.contor.exception;

/** Base class of every not-found exception; mapped to 404. */
public abstract class ResourceNotFoundException extends RuntimeException {
    protected ResourceNotFoundException(String message) {
        super(message);
    }
}
