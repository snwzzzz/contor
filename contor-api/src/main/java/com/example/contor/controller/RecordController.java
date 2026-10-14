package com.example.contor.controller;

import com.example.contor.api.RecordsApi;
import com.example.contor.api.model.EmailPageDto;
import com.example.contor.api.model.NewRecordCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.api.model.UpdateRecordCommand;
import com.example.contor.service.MailService;
import com.example.contor.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class RecordController implements RecordsApi {

    private final RecordService recordService;
    private final MailService mailService;

    @Override
    public ResponseEntity<EmailPageDto> listRecordEmails(Long collectionId, Long recordId, Integer page, Integer size) {
        return ResponseEntity.ok(mailService.listRecordEmails(collectionId, recordId, page, size));
    }

    @Override
    public ResponseEntity<RecordPageDto> listRecords(Long collectionId, List<String> filter, String q, String sort, Integer page, Integer size) {
        return ResponseEntity.ok(recordService.listRecords(collectionId, filter, q, sort, page, size));
    }

    @Override
    public ResponseEntity<RecordDto> getRecord(Long collectionId, Long recordId) {
        return ResponseEntity.ok(recordService.getRecord(collectionId, recordId));
    }

    @Override
    public ResponseEntity<RecordDto> createRecord(Long collectionId, NewRecordCommand newRecordCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recordService.createRecord(collectionId, newRecordCommand));
    }

    @Override
    public ResponseEntity<RecordDto> updateRecord(Long collectionId, Long recordId, UpdateRecordCommand updateRecordCommand) {
        return ResponseEntity.ok(recordService.updateRecord(collectionId, recordId, updateRecordCommand));
    }

    @Override
    public ResponseEntity<Void> deleteRecord(Long collectionId, Long recordId) {
        recordService.deleteRecord(collectionId, recordId);
        return ResponseEntity.noContent().build();
    }
}
