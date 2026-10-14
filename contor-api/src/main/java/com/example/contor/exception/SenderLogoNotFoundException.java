package com.example.contor.exception;

public class SenderLogoNotFoundException extends ResourceNotFoundException {
    public SenderLogoNotFoundException(String domain) {
        super("No logo for sender domain: " + domain);
    }
}
