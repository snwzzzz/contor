package com.example.contor.exception;

public class ReportNotFoundException extends ResourceNotFoundException {

    public ReportNotFoundException(Long id) {
        super("Report not found with id: " + id);
    }
}
