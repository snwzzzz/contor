package com.example.contor.exception;

public class AnalysisRunNotFoundException extends ResourceNotFoundException {
    public AnalysisRunNotFoundException() {
        super("No inbox analysis has run yet");
    }

    public AnalysisRunNotFoundException(Long id) {
        super("Analysis run " + id + " not found");
    }
}
