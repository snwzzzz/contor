package com.example.contor.persistence;

import com.example.contor.domain.AutonomyScopeType;
import com.example.contor.domain.AutonomySetting;
import com.example.contor.domain.ProposalKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AutonomySettingRepository extends JpaRepository<AutonomySetting, AutonomySetting.AutonomySettingId> {

    /** A {@code null} scopeId matches rows whose scope_id is null (Spring Data derives {@code is null}). */
    Optional<AutonomySetting> findByActionKindAndScopeTypeAndScopeId(ProposalKind actionKind, AutonomyScopeType scopeType, Long scopeId);
}
