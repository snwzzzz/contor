package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Report.ReportId> {

    /** The reports started on a record (follow-ups excluded), newest first. */
    List<Report> findByCollection_IdAndRecordIdAndParentReportIsNullOrderByCreatedAtDesc(Collection.CollectionId collectionId, Long recordId);

    List<Report> findByParentReport_IdOrderByCreatedAtAsc(Report.ReportId parentReportId);
}
