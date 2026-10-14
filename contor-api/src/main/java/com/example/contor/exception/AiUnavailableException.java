package com.example.contor.exception;

/** The AI cannot do this right now: Ollama unreachable, models missing, or a run already active (409). */
public class AiUnavailableException extends ConflictException {
    public AiUnavailableException(String message) {
        super(message);
    }
}
