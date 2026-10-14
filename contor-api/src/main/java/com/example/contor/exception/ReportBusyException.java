package com.example.contor.exception;

/** A follow-up or regeneration while the report is still being generated (409). */
public class ReportBusyException extends ConflictException {
    public ReportBusyException(Long id) {
        super("Report " + id + " is still being generated");
    }
}
