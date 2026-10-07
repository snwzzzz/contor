package com.example.contor.controller;

import com.example.contor.api.MailApi;
import com.example.contor.api.model.*;
import com.example.contor.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class MailController implements MailApi {

    private final MailService mailService;

    @PreAuthorize("hasRole('OWNER')")
    @Override
    public ResponseEntity<MailAuthorizeUrlDto> connectMailAccount(ConnectMailAccountCommand command) {
        return ResponseEntity.ok(mailService.connectAccount(command));
    }

    @Override
    public ResponseEntity<Void> mailAccountCallback(String state, String code, String error, String errorDescription) {
        mailService.handleCallback(state, code, error, errorDescription);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/settings/mail?connected=true"))
                .build();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Override
    public ResponseEntity<MailSyncStatusDto> syncMailAccount(Long accountId) {
        return ResponseEntity.ok(mailService.syncAccount(accountId));
    }

    @PreAuthorize("hasRole('OWNER')")
    @Override
    public ResponseEntity<Void> disconnectMailAccount(Long accountId) {
        mailService.disconnectAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MailSyncStatusDto> getMailAccountSyncStatus(Long accountId) {
        return ResponseEntity.ok(mailService.getSyncStatus(accountId));
    }

    @Override
    public ResponseEntity<List<MailAccountDto>> listMailAccounts() {
        return ResponseEntity.ok(mailService.listMailAccounts());
    }

    @Override
    public ResponseEntity<EmailPageDto> listEmails(Long accountId, Long labelId, EmailProcessingState processingState, String q,
                                                   Integer page, Integer size) {
        return ResponseEntity.ok(mailService.listEmails(accountId, labelId, processingState, q, page, size));
    }

    @Override
    public ResponseEntity<EmailDto> getEmail(Long emailId) {
        return ResponseEntity.ok(mailService.getEmail(emailId));
    }
}
