package com.example.contor.exception;

/** The provider no longer accepts a stored sync cursor (Graph 410, Gmail 404 on the history): sync again from scratch. */
public class SyncCursorExpiredException extends RuntimeException {

    public SyncCursorExpiredException(String message) {
        super(message);
    }
}
