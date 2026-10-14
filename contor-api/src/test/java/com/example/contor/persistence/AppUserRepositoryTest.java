package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.Username;
import com.example.contor.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class AppUserRepositoryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void can_save_and_find_by_username() {
        appUserRepository.save(Fixtures.user("owner"));

        var found = appUserRepository.findByUsername(new Username("owner"));

        assertThat(found).isPresent();
        assertThat(found.get().getId().id()).isNotNull();
        assertThat(found.get().getRole()).isEqualTo(UserRole.OWNER);
        assertThat(found.get().isTotpEnabled()).isFalse();
        assertThat(found.get().getFailedLoginAttempts()).isZero();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void knows_whether_a_username_exists() {
        assertThat(appUserRepository.existsByUsername(new Username("owner"))).isFalse();

        appUserRepository.save(Fixtures.user("owner"));

        assertThat(appUserRepository.existsByUsername(new Username("owner"))).isTrue();
    }

    @Test
    void totp_secret_is_encrypted_at_rest() {
        var user = Fixtures.user("owner");
        user.setTotpSecret("JBSWY3DPEHPK3PXP");
        var saved = appUserRepository.saveAndFlush(user);

        var stored = jdbcTemplate.queryForObject(
                "select totp_secret_enc from \"app_user\" where id = ?", String.class, saved.getId().id());

        assertThat(stored).startsWith("v1:").doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(appUserRepository.findById(saved.getId()).orElseThrow().getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void sets_last_modified_on_update() {
        var saved = appUserRepository.saveAndFlush(Fixtures.user("owner"));
        var createdAt = saved.getCreatedAt();

        saved.setFailedLoginAttempts(1);
        var updated = appUserRepository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getLastModified()).isAfterOrEqualTo(createdAt);
    }
}
