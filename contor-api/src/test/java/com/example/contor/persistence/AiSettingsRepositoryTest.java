package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.AiPermissionLevel;
import com.example.contor.domain.AiSettings;
import com.example.contor.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class AiSettingsRepositoryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AiSettingsRepository aiSettingsRepository;

    private AiSettings settings(AppUser user) {
        return AiSettings.builder().user(user).mailAccess(true).historyDays(90)
                .categories(AiPermissionLevel.SUGGEST).records(AiPermissionLevel.AUTO)
                .calendar(AiPermissionLevel.AUTO).tasks(AiPermissionLevel.OFF).build();
    }

    @Test
    void finds_the_settings_of_a_user() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));
        appUserRepository.save(Fixtures.user("other"));
        aiSettingsRepository.saveAndFlush(settings(owner));

        var found = aiSettingsRepository.findByUser_Id(owner.getId()).orElseThrow();

        assertThat(found.getRecords()).isEqualTo(AiPermissionLevel.AUTO);
        assertThat(found.getTasks()).isEqualTo(AiPermissionLevel.OFF);
        assertThat(found.getHistoryDays()).isEqualTo(90);
    }

    @Test
    void finds_users_who_completed_the_setup_with_mail_access() {
        AppUser onboarded = appUserRepository.save(Fixtures.user("onboarded"));
        AppUser pending = appUserRepository.save(Fixtures.user("pending"));
        AppUser revoked = appUserRepository.save(Fixtures.user("revoked"));
        AiSettings done = settings(onboarded);
        done.setOnboardedAt(Instant.parse("2026-01-01T00:00:00Z"));
        AiSettings withoutAccess = settings(revoked);
        withoutAccess.setMailAccess(false);
        withoutAccess.setOnboardedAt(Instant.parse("2026-01-01T00:00:00Z"));
        aiSettingsRepository.save(done);
        aiSettingsRepository.save(settings(pending));
        aiSettingsRepository.saveAndFlush(withoutAccess);

        assertThat(aiSettingsRepository.findByMailAccessTrueAndOnboardedAtIsNotNull())
                .extracting(s -> s.getUser().getId()).containsExactly(onboarded.getId());
    }

    @Test
    void reply_drafts_default_to_off_and_never_allow_auto() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));
        AiSettings saved = aiSettingsRepository.saveAndFlush(settings(owner));
        assertThat(aiSettingsRepository.findByUser_Id(owner.getId()).orElseThrow().getReplies()).isEqualTo(AiPermissionLevel.OFF);

        saved.setReplies(AiPermissionLevel.AUTO);

        assertThatThrownBy(() -> aiSettingsRepository.saveAndFlush(saved))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_settings_replies");
    }

    @Test
    void allows_one_row_per_user() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));
        aiSettingsRepository.saveAndFlush(settings(owner));

        assertThatThrownBy(() -> aiSettingsRepository.saveAndFlush(settings(owner)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ai_settings_user_id");
    }

    @Test
    void external_apis_are_suggestions_by_default() {
        AppUser owner = appUserRepository.save(Fixtures.user("owner"));

        var saved = aiSettingsRepository.saveAndFlush(AiSettings.builder().user(owner).build());

        assertThat(saved.getExternalApis()).isEqualTo(AiPermissionLevel.SUGGEST);
    }
}
