package com.example.contor.domain;

/** AI processing state of an email. */
public enum ProcessingState {
    NEW, QUEUED, PROCESSING, DONE, FAILED, SKIPPED
}
