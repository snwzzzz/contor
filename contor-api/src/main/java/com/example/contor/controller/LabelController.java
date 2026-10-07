package com.example.contor.controller;

import com.example.contor.api.LabelsApi;
import com.example.contor.api.model.LabelDto;
import com.example.contor.api.model.NewLabelCommand;
import com.example.contor.api.model.UpdateLabelCommand;
import com.example.contor.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LabelController implements LabelsApi {

    private final LabelService labelService;

    @Override
    public ResponseEntity<List<LabelDto>> listLabels() {
        return ResponseEntity.ok(labelService.findAll());
    }

    @Override
    public ResponseEntity<LabelDto> getLabel(Long labelId) {
        return ResponseEntity.ok(labelService.findById(labelId));
    }

    @Override
    public ResponseEntity<LabelDto> createLabel(NewLabelCommand newLabelCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labelService.createLabel(newLabelCommand));
    }

    @Override
    public ResponseEntity<LabelDto> updateLabel(Long labelId, UpdateLabelCommand updateLabelCommand) {
        return ResponseEntity.ok(labelService.updateLabel(labelId, updateLabelCommand));
    }

    @Override
    public ResponseEntity<Void> deleteLabel(Long labelId) {
        labelService.deleteLabel(labelId);
        return ResponseEntity.noContent().build();
    }
}
