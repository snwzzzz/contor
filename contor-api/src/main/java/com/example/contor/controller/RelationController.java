package com.example.contor.controller;

import com.example.contor.api.RelationsApi;
import com.example.contor.api.model.NewRelationCommand;
import com.example.contor.api.model.NewRelationTypeCommand;
import com.example.contor.api.model.RelationDto;
import com.example.contor.api.model.RelationTypeDto;
import com.example.contor.api.model.UpdateRelationCommand;
import com.example.contor.api.model.UpdateRelationTypeCommand;
import com.example.contor.service.RelationService;
import com.example.contor.service.RelationTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Relation types are schema (OWNER only); relations between records are data. */
@RequiredArgsConstructor
@RestController
public class RelationController implements RelationsApi {

    private final RelationTypeService relationTypeService;
    private final RelationService relationService;

    @Override
    public ResponseEntity<List<RelationTypeDto>> listRelationTypes() {
        return ResponseEntity.ok(relationTypeService.findAll());
    }

    @Override
    public ResponseEntity<RelationTypeDto> getRelationType(Long relationTypeId) {
        return ResponseEntity.ok(relationTypeService.findById(relationTypeId));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<RelationTypeDto> createRelationType(NewRelationTypeCommand newRelationTypeCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(relationTypeService.createRelationType(newRelationTypeCommand));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<RelationTypeDto> updateRelationType(Long relationTypeId, UpdateRelationTypeCommand updateRelationTypeCommand) {
        return ResponseEntity.ok(relationTypeService.updateRelationType(relationTypeId, updateRelationTypeCommand));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> deleteRelationType(Long relationTypeId) {
        relationTypeService.deleteRelationType(relationTypeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<RelationDto>> listRelations(Long collectionId, Long recordId) {
        return ResponseEntity.ok(relationService.listRelations(collectionId, recordId));
    }

    @Override
    public ResponseEntity<RelationDto> getRelation(Long relationId) {
        return ResponseEntity.ok(relationService.findById(relationId));
    }

    @Override
    public ResponseEntity<RelationDto> createRelation(NewRelationCommand newRelationCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(relationService.createRelation(newRelationCommand));
    }

    @Override
    public ResponseEntity<RelationDto> updateRelation(Long relationId, UpdateRelationCommand updateRelationCommand) {
        return ResponseEntity.ok(relationService.updateRelation(relationId, updateRelationCommand));
    }

    @Override
    public ResponseEntity<Void> deleteRelation(Long relationId) {
        relationService.deleteRelation(relationId);
        return ResponseEntity.noContent().build();
    }
}
