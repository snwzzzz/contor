package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.MailAccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class MailAccountRepositoryTest {

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void can_save_and_find_by_owner() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));

        var accounts = mailAccountRepository.findByOwner_Id(owner.getId());

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getStatus()).isEqualTo(MailAccountStatus.ACTIVE);
        assertThat(accounts.get(0).getEmailAddress().value()).isEqualTo("me@example.com");
    }

    @Test
    void a_new_mailbox_is_read_by_the_ai_and_shown_in_the_app() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var saved = mailAccountRepository.saveAndFlush(Fixtures.mailAccount(owner, "me@example.com"));

        assertThat(saved.isAiEnabled()).isTrue();
        assertThat(saved.isHidden()).isFalse();
        Map<String, Object> row = jdbcTemplate.queryForMap("select ai_enabled, hidden from \"mail_account\" where id = ?", saved.getId().id());
        assertThat(row).containsEntry("ai_enabled", true).containsEntry("hidden", false);
    }

    @Test
    void existing_rows_get_the_defaults_from_the_migration() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        appUserRepository.flush();
        jdbcTemplate.update("insert into \"mail_account\" (id, owner_user_id, provider, email_address, status, created_at, last_modified) "
                + "values (nextval('mail_account_seq'), ?, 'MICROSOFT_GRAPH', 'old@example.com', 'ACTIVE', now(), now())", owner.getId().id());

        Map<String, Object> row = jdbcTemplate.queryForMap("select ai_enabled, hidden from \"mail_account\" where email_address = 'old@example.com'");

        assertThat(row).containsEntry("ai_enabled", true).containsEntry("hidden", false);
    }

    @Test
    void oauth_tokens_are_encrypted_at_rest() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var account = Fixtures.mailAccount(owner, "me@example.com");
        account.setAccessToken("access-plain");
        account.setRefreshToken("refresh-plain");
        var saved = mailAccountRepository.saveAndFlush(account);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select access_token_enc, refresh_token_enc from \"mail_account\" where id = ?", saved.getId().id());

        assertThat((String) row.get("access_token_enc")).startsWith("v1:").doesNotContain("access-plain");
        assertThat((String) row.get("refresh_token_enc")).startsWith("v1:").doesNotContain("refresh-plain");
        var reloaded = mailAccountRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAccessToken()).isEqualTo("access-plain");
        assertThat(reloaded.getRefreshToken()).isEqualTo("refresh-plain");
    }

    @Test
    void stores_the_profile_photo() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var account = Fixtures.mailAccount(owner, "me@example.com");
        account.setPhoto(new byte[] {1, 2, 3});
        account.setPhotoContentType("image/png");
        account.setPhotoCheckedAt(Instant.parse("2026-01-01T00:00:00Z"));
        var saved = mailAccountRepository.saveAndFlush(account);

        var reloaded = mailAccountRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPhoto()).containsExactly(1, 2, 3);
        assertThat(reloaded.getPhotoContentType()).isEqualTo("image/png");
        assertThat(reloaded.getPhotoCheckedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
