package com.example.contor.exception;

/** "Call now" for an app that is turned off (409). */
public class ExternalApiDisabledException extends ConflictException {
    public ExternalApiDisabledException(String name) {
        super(name + " is turned off; turn it on to call it");
    }
}
