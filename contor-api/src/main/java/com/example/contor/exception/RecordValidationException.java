package com.example.contor.exception;

import lombok.Getter;

import java.util.List;

/** Every violated field rule of a record at once ({@code "<field>: <problem>"}); the message joins them with "; ". 400. */
@Getter
public class RecordValidationException extends RuntimeException {

    private final List<String> violations;

    public RecordValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }
}
