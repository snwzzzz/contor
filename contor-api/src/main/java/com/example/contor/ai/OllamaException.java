package com.example.contor.ai;

/** Ollama could not be reached or answered with an error. */
public class OllamaException extends RuntimeException {

    private final boolean serverError;

    public OllamaException(String message) {
        this(message, false);
    }

    /** {@code serverError}: Ollama answered 5xx (e.g. the runner died while loading the model); worth one retry. */
    public OllamaException(String message, boolean serverError) {
        super(message);
        this.serverError = serverError;
    }

    public OllamaException(String message, Throwable cause) {
        super(message, cause);
        this.serverError = false;
    }

    public boolean isServerError() {
        return serverError;
    }
}
