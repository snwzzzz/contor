package com.example.contor.exception;

/** The request conflicts with the current state of a resource (409). */
public abstract class ConflictException extends RuntimeException {
    protected ConflictException(String message) {
        super(message);
    }
}
