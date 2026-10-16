package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface ReportRepository extends JpaRepository<Report, Report.ReportId> {

    /** The reports started on a record (follow-ups excluded), newest first. */
    List<Report> findByCollection_IdAndRecordIdAndParentReportIsNullOrderByCreatedAtDesc(Collection.CollectionId collectionId, Long recordId);

    /** The reports started on a whole collection (follow-ups excluded), newest first. */
    List<Report> findByCollection_IdAndRecordIdIsNullAndParentReportIsNullOrderByCreatedAtDesc(Collection.CollectionId collectionId);

    List<Report> findByParentReport_IdOrderByCreatedAtAsc(Report.ReportId parentReportId);

    /** Reports in one of {@code statuses} (e.g. still being generated). */
    List<Report> findByStatusIn(Set<ReportStatus> statuses);
}
