package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.EmailHeader;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ProcessingState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class EmailMessageRepositoryTest {

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void can_save_and_find_by_provider_message_id_with_json_columns() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var email = Fixtures.email(account, "AAMk-1");
        email.setHeaders(List.of(new EmailHeader("Received", "a"), new EmailHeader("Received", "b")));
        emailMessageRepository.saveAndFlush(email);

        var found = emailMessageRepository.findByAccount_IdAndProviderMessageId(account.getId(), "AAMk-1");

        assertThat(found).isPresent();
        assertThat(found.get().getProcessingState()).isEqualTo(ProcessingState.NEW);
        assertThat(found.get().getProcessingVersion()).isZero();
        assertThat(found.get().isRead()).isFalse();
        assertThat(found.get().getToRecipients()).containsExactly(new EmailRecipient("Me", "me@example.com"));
        assertThat(found.get().getHeaders()).hasSize(2);
    }

    @Test
    void provider_message_id_is_unique_per_account() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var first = mailAccountRepository.save(Fixtures.mailAccount(owner, "a@example.com"));
        var second = mailAccountRepository.save(Fixtures.mailAccount(owner, "b@example.com"));
        emailMessageRepository.saveAndFlush(Fixtures.email(first, "AAMk-1"));
        emailMessageRepository.saveAndFlush(Fixtures.email(second, "AAMk-1"));

        assertThatThrownBy(() -> emailMessageRepository.saveAndFlush(Fixtures.email(first, "AAMk-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void finds_by_processing_state() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var queued = Fixtures.email(account, "AAMk-1");
        queued.setProcessingState(ProcessingState.QUEUED);
        emailMessageRepository.save(queued);
        emailMessageRepository.save(Fixtures.email(account, "AAMk-2"));

        assertThat(emailMessageRepository.findByProcessingState(ProcessingState.QUEUED))
                .extracting(e -> e.getProviderMessageId())
                .containsExactly("AAMk-1");
    }
}
