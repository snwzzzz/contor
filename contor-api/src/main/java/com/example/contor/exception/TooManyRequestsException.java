package com.example.contor.exception;

/** Rate limit exceeded (429). */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
