package com.example.contor.exception;

public class MailAccountNotFoundException extends ResourceNotFoundException {
    public MailAccountNotFoundException(Long id) {
        super("Mail account not found with id: " + id);
    }
}
