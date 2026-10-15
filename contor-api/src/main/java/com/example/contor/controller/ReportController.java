package com.example.contor.controller;

import com.example.contor.api.ReportsApi;
import com.example.contor.api.model.NewCollectionReportCommand;
import com.example.contor.api.model.NewReportCommand;
import com.example.contor.api.model.NewReportQuestionCommand;
import com.example.contor.api.model.ReportDto;
import com.example.contor.api.model.ReportSummaryDto;
import com.example.contor.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class ReportController implements ReportsApi {

    private final ReportService reportService;

    @Override
    public ResponseEntity<List<ReportSummaryDto>> listRecordReports(Long collectionId, Long recordId) {
        return ResponseEntity.ok(reportService.listRecordReports(collectionId, recordId));
    }

    @Override
    public ResponseEntity<ReportDto> createReport(Long collectionId, Long recordId, NewReportCommand newReportCommand) {
        return ResponseEntity.accepted().body(reportService.createReport(collectionId, recordId, newReportCommand));
    }

    @Override
    public ResponseEntity<List<ReportSummaryDto>> listCollectionReports(Long collectionId) {
        return ResponseEntity.ok(reportService.listCollectionReports(collectionId));
    }

    @Override
    public ResponseEntity<ReportDto> createCollectionReport(Long collectionId, NewCollectionReportCommand newCollectionReportCommand) {
        return ResponseEntity.accepted().body(reportService.createCollectionReport(collectionId, newCollectionReportCommand));
    }

    @Override
    public ResponseEntity<ReportDto> getReport(Long reportId) {
        return ResponseEntity.ok(reportService.getReport(reportId));
    }

    @Override
    public ResponseEntity<ReportDto> askReportQuestion(Long reportId, NewReportQuestionCommand newReportQuestionCommand) {
        return ResponseEntity.accepted().body(reportService.askReportQuestion(reportId, newReportQuestionCommand));
    }

    @Override
    public ResponseEntity<ReportDto> regenerateReport(Long reportId) {
        return ResponseEntity.accepted().body(reportService.regenerateReport(reportId));
    }

    @Override
    public ResponseEntity<Void> deleteReport(Long reportId) {
        reportService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }
}
