package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.AiAnalysisRun;
import com.example.contor.domain.AnalysisRunStatus;
import com.example.contor.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class AiAnalysisRunRepositoryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    private AiAnalysisRun run(AppUser user, AnalysisRunStatus status, String createdAt) {
        AiAnalysisRun run = AiAnalysisRun.builder().user(user).status(status).build();
        run.setCreatedAt(Instant.parse(createdAt));
        return run;
    }

    @Test
    void finds_the_latest_run_of_a_user() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));
        AppUser other = appUserRepository.save(Fixtures.user("other"));
        aiAnalysisRunRepository.save(run(owner, AnalysisRunStatus.COMPLETED, "2026-01-01T00:00:00Z"));
        aiAnalysisRunRepository.save(run(owner, AnalysisRunStatus.FAILED, "2026-02-01T00:00:00Z"));
        aiAnalysisRunRepository.saveAndFlush(run(other, AnalysisRunStatus.QUEUED, "2026-03-01T00:00:00Z"));

        var latest = aiAnalysisRunRepository.findFirstByUser_IdOrderByCreatedAtDesc(owner.getId()).orElseThrow();

        assertThat(latest.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
    }

    @Test
    void finds_active_runs() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));
        aiAnalysisRunRepository.save(run(owner, AnalysisRunStatus.COMPLETED, "2026-01-01T00:00:00Z"));
        aiAnalysisRunRepository.saveAndFlush(run(owner, AnalysisRunStatus.EXTRACTING, "2026-02-01T00:00:00Z"));

        assertThat(aiAnalysisRunRepository.existsByUser_IdAndStatusIn(owner.getId(), AnalysisRunStatus.ACTIVE)).isTrue();
        assertThat(aiAnalysisRunRepository.findByStatusIn(AnalysisRunStatus.ACTIVE))
                .extracting(AiAnalysisRun::getStatus).containsExactly(AnalysisRunStatus.EXTRACTING);
    }

    @Test
    void new_runs_start_queued_with_zero_counters() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));

        var saved = aiAnalysisRunRepository.saveAndFlush(AiAnalysisRun.builder().user(owner).build());

        assertThat(saved.getStatus()).isEqualTo(AnalysisRunStatus.QUEUED);
        assertThat(saved.getProcessedEmails()).isZero();
        assertThat(saved.getProposalsCreated()).isZero();
    }

    @Test
    void counters_cannot_be_negative() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));

        assertThatThrownBy(() -> aiAnalysisRunRepository.saveAndFlush(AiAnalysisRun.builder().user(owner).processedEmails(-1).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_analysis_run_counters");
    }
}
