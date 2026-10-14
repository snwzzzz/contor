package com.example.contor.exception;

public class MailAccountPhotoNotFoundException extends ResourceNotFoundException {
    public MailAccountPhotoNotFoundException(Long accountId) {
        super("No profile photo for mail account with id: " + accountId);
    }
}
