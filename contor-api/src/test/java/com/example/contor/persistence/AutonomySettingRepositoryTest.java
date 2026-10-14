package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.AutonomyLevel;
import com.example.contor.domain.AutonomyScopeType;
import com.example.contor.domain.AutonomySetting;
import com.example.contor.domain.ProposalKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class AutonomySettingRepositoryTest {

    @Autowired
    private AutonomySettingRepository autonomySettingRepository;

    @Test
    void finds_setting_by_kind_and_scope_and_defaults_to_suggest() {
        autonomySettingRepository.save(AutonomySetting.builder().actionKind(ProposalKind.SET_LABEL).scopeType(AutonomyScopeType.GLOBAL).build());
        autonomySettingRepository.save(AutonomySetting.builder().actionKind(ProposalKind.SET_LABEL).scopeType(AutonomyScopeType.LABEL)
                .scopeId(3L).level(AutonomyLevel.AUTO).build());

        var global = autonomySettingRepository.findByActionKindAndScopeTypeAndScopeId(ProposalKind.SET_LABEL, AutonomyScopeType.GLOBAL, null);
        var label = autonomySettingRepository.findByActionKindAndScopeTypeAndScopeId(ProposalKind.SET_LABEL, AutonomyScopeType.LABEL, 3L);

        assertThat(global).map(AutonomySetting::getLevel).contains(AutonomyLevel.SUGGEST);
        assertThat(label).map(AutonomySetting::getLevel).contains(AutonomyLevel.AUTO);
    }

    @Test
    void only_one_setting_per_kind_and_scope_even_without_scope_id() {
        autonomySettingRepository.saveAndFlush(AutonomySetting.builder().actionKind(ProposalKind.CREATE_RECORD).scopeType(AutonomyScopeType.GLOBAL).build());

        assertThatThrownBy(() -> autonomySettingRepository.saveAndFlush(
                AutonomySetting.builder().actionKind(ProposalKind.CREATE_RECORD).scopeType(AutonomyScopeType.GLOBAL).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
