package com.example.contor.persistence;

import com.example.contor.domain.Collection;
import com.example.contor.domain.Report;
import com.example.contor.domain.ReportCitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportCitationRepository extends JpaRepository<ReportCitation, Long> {

    /** Reports with a sentence citing the record or one of its fields. */
    @Query("""
            select distinct c.claim.section.report.id from ReportCitation c
            where c.collection.id = :collectionId and c.recordId = :recordId""")
    List<Report.ReportId> findReportIdsCiting(@Param("collectionId") Collection.CollectionId collectionId, @Param("recordId") Long recordId);
}
