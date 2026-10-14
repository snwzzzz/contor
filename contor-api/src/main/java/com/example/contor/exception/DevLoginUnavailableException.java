package com.example.contor.exception;

public class DevLoginUnavailableException extends ResourceNotFoundException {
    public DevLoginUnavailableException() {
        super("Dev sign-in is not available");
    }
}
