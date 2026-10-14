package com.example.contor.persistence;

import com.example.contor.domain.AiAnalysisRun;
import com.example.contor.domain.AnalysisRunStatus;
import com.example.contor.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, AiAnalysisRun.AiAnalysisRunId> {

    Optional<AiAnalysisRun> findFirstByUser_IdOrderByCreatedAtDesc(AppUser.AppUserId userId);

    boolean existsByUser_IdAndStatusIn(AppUser.AppUserId userId, Collection<AnalysisRunStatus> statuses);

    List<AiAnalysisRun> findByStatusIn(Collection<AnalysisRunStatus> statuses);
}
