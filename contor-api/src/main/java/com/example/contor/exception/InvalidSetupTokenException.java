package com.example.contor.exception;

/** Wrong or already used setup token (403). */
public class InvalidSetupTokenException extends RuntimeException {
    public InvalidSetupTokenException() {
        super("Invalid setup token");
    }
}
