package com.example.contor.exception;

/** A reply draft cannot be suggested for this email right now (settings off, own sent mail) (409). */
public class ReplySuggestionUnavailableException extends ConflictException {
    public ReplySuggestionUnavailableException(String message) {
        super(message);
    }
}
