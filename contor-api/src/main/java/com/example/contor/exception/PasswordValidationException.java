package com.example.contor.exception;

import lombok.Getter;

import java.util.List;

/** All violated password rules at once; the message joins them with "; ". Mapped to 400. */
@Getter
public class PasswordValidationException extends RuntimeException {

    private final List<String> violations;

    public PasswordValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }
}
