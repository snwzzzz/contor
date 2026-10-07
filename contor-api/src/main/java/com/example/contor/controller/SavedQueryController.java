package com.example.contor.controller;

import com.example.contor.api.SavedQueriesApi;
import com.example.contor.api.model.NewSavedQueryCommand;
import com.example.contor.api.model.SavedQueryDto;
import com.example.contor.api.model.UpdateSavedQueryCommand;
import com.example.contor.service.SavedQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class SavedQueryController implements SavedQueriesApi {

    private final SavedQueryService savedQueryService;

    @Override
    public ResponseEntity<List<SavedQueryDto>> listSavedQueries() {
        return ResponseEntity.ok(savedQueryService.findAll());
    }

    @Override
    public ResponseEntity<SavedQueryDto> getSavedQuery(Long savedQueryId) {
        return ResponseEntity.ok(savedQueryService.findById(savedQueryId));
    }

    @Override
    public ResponseEntity<SavedQueryDto> createSavedQuery(NewSavedQueryCommand newSavedQueryCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedQueryService.createSavedQuery(newSavedQueryCommand));
    }

    @Override
    public ResponseEntity<SavedQueryDto> updateSavedQuery(Long savedQueryId, UpdateSavedQueryCommand updateSavedQueryCommand) {
        return ResponseEntity.ok(savedQueryService.updateSavedQuery(savedQueryId, updateSavedQueryCommand));
    }

    @Override
    public ResponseEntity<Void> deleteSavedQuery(Long savedQueryId) {
        savedQueryService.deleteSavedQuery(savedQueryId);
        return ResponseEntity.noContent().build();
    }
}
