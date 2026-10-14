package com.example.contor.exception;

/** Setup was already completed; the endpoint is permanently gone (404). */
public class SetupNotAvailableException extends ResourceNotFoundException {
    public SetupNotAvailableException() {
        super("Setup has already been completed");
    }
}
