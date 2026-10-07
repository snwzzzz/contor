package com.example.contor.exception;

/** Unknown user, wrong password or locked account. Deliberately one message for all three (401). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
