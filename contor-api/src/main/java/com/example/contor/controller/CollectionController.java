package com.example.contor.controller;

import com.example.contor.api.CollectionsApi;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.api.model.UpdateCollectionCommand;
import com.example.contor.service.CollectionSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Schema changes are OWNER-only (checked here, so internal callers like the seeder can use the service). */
@RequiredArgsConstructor
@RestController
public class CollectionController implements CollectionsApi {

    private final CollectionSchemaService collectionSchemaService;

    @Override
    public ResponseEntity<List<CollectionDto>> listCollections() {
        return ResponseEntity.ok(collectionSchemaService.findAll());
    }

    @Override
    public ResponseEntity<CollectionDto> getCollection(Long collectionId) {
        return ResponseEntity.ok(collectionSchemaService.findById(collectionId));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CollectionDto> createCollection(NewCollectionCommand newCollectionCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collectionSchemaService.createCollection(newCollectionCommand));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CollectionDto> updateCollection(Long collectionId, UpdateCollectionCommand updateCollectionCommand) {
        return ResponseEntity.ok(collectionSchemaService.updateCollection(collectionId, updateCollectionCommand));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CollectionDto> addField(Long collectionId, NewCollectionFieldCommand newCollectionFieldCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collectionSchemaService.addField(collectionId, newCollectionFieldCommand));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CollectionDto> archiveField(Long collectionId, Long fieldId) {
        return ResponseEntity.ok(collectionSchemaService.archiveField(collectionId, fieldId));
    }
}
