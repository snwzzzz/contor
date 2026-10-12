package com.example.contor.exception;

import com.example.contor.domain.AnalysisRunStatus;

/** The analysis run is not in a state that allows this (409). */
public class AnalysisRunStateException extends ConflictException {
    public AnalysisRunStateException(Long id, AnalysisRunStatus status) {
        super("Analysis run " + id + " is " + status);
    }
}
