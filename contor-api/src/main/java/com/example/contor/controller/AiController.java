package com.example.contor.controller;

import com.example.contor.api.AiApi;
import com.example.contor.api.model.AiSettingsDto;
import com.example.contor.api.model.AiStatusDto;
import com.example.contor.api.model.AnalysisRunDto;
import com.example.contor.api.model.UpdateAiSettingsCommand;
import com.example.contor.service.AiAnalysisService;
import com.example.contor.service.AiModelService;
import com.example.contor.service.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class AiController implements AiApi {

    private final AiModelService aiModelService;
    private final AiSettingsService aiSettingsService;
    private final AiAnalysisService aiAnalysisService;

    @Override
    public ResponseEntity<AiStatusDto> getAiStatus() {
        return ResponseEntity.ok(aiModelService.status());
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<AiStatusDto> pullAiModels() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(aiModelService.pullMissingModels());
    }

    @Override
    public ResponseEntity<AiSettingsDto> getAiSettings() {
        return ResponseEntity.ok(aiSettingsService.getSettings());
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<AiSettingsDto> updateAiSettings(UpdateAiSettingsCommand updateAiSettingsCommand) {
        return ResponseEntity.ok(aiSettingsService.updateSettings(updateAiSettingsCommand));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<AnalysisRunDto> startAnalysis() {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiAnalysisService.startAnalysis());
    }

    @Override
    public ResponseEntity<AnalysisRunDto> getLatestAnalysis() {
        return ResponseEntity.ok(aiAnalysisService.getLatestAnalysis());
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<AnalysisRunDto> continueAnalysis(Long runId) {
        return ResponseEntity.ok(aiAnalysisService.continueAnalysis(runId));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<AnalysisRunDto> cancelAnalysis(Long runId) {
        return ResponseEntity.ok(aiAnalysisService.cancelAnalysis(runId));
    }
}
