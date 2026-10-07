package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.MailAccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
