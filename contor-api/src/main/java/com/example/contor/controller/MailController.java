package com.example.contor.controller;

import com.example.contor.api.MailApi;
import com.example.contor.api.model.EmailDto;
import com.example.contor.api.model.EmailPageDto;
import com.example.contor.api.model.EmailProcessingState;
import com.example.contor.api.model.MailAccountDto;
import com.example.contor.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class MailController implements MailApi {

    private final MailService mailService;

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
