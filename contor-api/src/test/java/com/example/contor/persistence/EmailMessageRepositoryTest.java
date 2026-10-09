package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.ai.DiscoveryEmail;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.EmailHeader;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.ProcessingState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class EmailMessageRepositoryTest {

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private EmailEmbeddingRepository emailEmbeddingRepository;

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

    private EmailMessage email(MailAccount account, String id, String receivedAt, ProcessingState state) {
        EmailMessage email = Fixtures.email(account, id);
        email.setReceivedAt(Instant.parse(receivedAt));
        email.setProcessingState(state);
        email.setSubject("Subject " + id);
        email.setSnippet("Snippet " + id);
        return emailMessageRepository.save(email);
    }

    @Test
    void finds_ids_to_process_of_one_owner_oldest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var newer = email(mine, "A", "2026-03-01T00:00:00Z", ProcessingState.NEW);
        var older = email(mine, "B", "2026-02-01T00:00:00Z", ProcessingState.NEW);
        email(mine, "C", "2025-01-01T00:00:00Z", ProcessingState.NEW);
        email(mine, "D", "2026-02-15T00:00:00Z", ProcessingState.DONE);
        email(theirs, "E", "2026-02-15T00:00:00Z", ProcessingState.NEW);
        emailMessageRepository.flush();

        List<Long> ids = emailMessageRepository.findIdsForProcessing(owner.getId(), ProcessingState.NEW,
                Instant.parse("2026-01-01T00:00:00Z"), PageRequest.of(0, 10));

        assertThat(ids).containsExactly(older.getId().id(), newer.getId().id());
        assertThat(emailMessageRepository.findIdsForProcessing(owner.getId(), ProcessingState.NEW,
                Instant.parse("2026-01-01T00:00:00Z"), PageRequest.of(0, 1))).hasSize(1);
    }

    @Test
    void discovery_samples_skip_sent_mail() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        email(mine, "A", "2026-02-01T00:00:00Z", ProcessingState.NEW);
        var sent = email(mine, "B", "2026-03-01T00:00:00Z", ProcessingState.SKIPPED);
        sent.setFolder("sent");
        emailMessageRepository.flush();

        assertThat(emailMessageRepository.findDiscoveryEmails(owner.getId(), Instant.parse("2026-01-01T00:00:00Z"), PageRequest.of(0, 10)))
                .extracting(DiscoveryEmail::subject).containsExactly("Subject A");
    }

    @Test
    void lists_discovery_samples_of_one_owner_newest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        email(mine, "A", "2026-02-01T00:00:00Z", ProcessingState.NEW);
        email(mine, "B", "2026-03-01T00:00:00Z", ProcessingState.DONE);
        email(mine, "C", "2025-01-01T00:00:00Z", ProcessingState.NEW);
        email(theirs, "D", "2026-02-15T00:00:00Z", ProcessingState.NEW);
        emailMessageRepository.flush();

        List<DiscoveryEmail> samples = emailMessageRepository.findDiscoveryEmails(owner.getId(),
                Instant.parse("2026-01-01T00:00:00Z"), PageRequest.of(0, 10));

        assertThat(samples).extracting(DiscoveryEmail::subject).containsExactly("Subject B", "Subject A");
        assertThat(samples.getFirst().fromAddress()).isEqualTo("shop@example.com");
        assertThat(samples.getFirst().snippet()).isEqualTo("Snippet B");
    }

    @Test
    void marks_old_unprocessed_emails_of_one_owner() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var old = email(mine, "A", "2025-01-01T00:00:00Z", ProcessingState.NEW);
        var recent = email(mine, "B", "2026-03-01T00:00:00Z", ProcessingState.NEW);
        var foreign = email(theirs, "C", "2025-01-01T00:00:00Z", ProcessingState.NEW);
        emailMessageRepository.flush();

        int updated = emailMessageRepository.updateProcessingStateBefore(owner.getId(), ProcessingState.NEW, ProcessingState.SKIPPED,
                Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(updated).isEqualTo(1);
        assertThat(emailMessageRepository.findById(old.getId()).orElseThrow().getProcessingState()).isEqualTo(ProcessingState.SKIPPED);
        assertThat(emailMessageRepository.findById(recent.getId()).orElseThrow().getProcessingState()).isEqualTo(ProcessingState.NEW);
        assertThat(emailMessageRepository.findById(foreign.getId()).orElseThrow().getProcessingState()).isEqualTo(ProcessingState.NEW);
    }

    @Test
    void resets_interrupted_emails() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var interrupted = email(account, "A", "2026-01-01T00:00:00Z", ProcessingState.PROCESSING);
        email(account, "B", "2026-01-01T00:00:00Z", ProcessingState.DONE);
        emailMessageRepository.flush();

        assertThat(emailMessageRepository.updateProcessingState(ProcessingState.PROCESSING, ProcessingState.NEW)).isEqualTo(1);
        assertThat(emailMessageRepository.findById(interrupted.getId()).orElseThrow().getProcessingState()).isEqualTo(ProcessingState.NEW);
    }

    @Test
    void finds_the_inbox_mail_without_a_vector_for_the_model_newest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var old = email(mine, "old", "2026-01-01T00:00:00Z", ProcessingState.DONE);
        var recent = email(mine, "recent", "2026-03-01T00:00:00Z", ProcessingState.DONE);
        var embedded = email(mine, "embedded", "2026-02-01T00:00:00Z", ProcessingState.DONE);
        var embeddedWithOldModel = email(mine, "oldmodel", "2026-02-15T00:00:00Z", ProcessingState.DONE);
        var sent = email(mine, "sent", "2026-04-01T00:00:00Z", ProcessingState.SKIPPED);
        sent.setFolder("sent");
        email(theirs, "foreign", "2026-05-01T00:00:00Z", ProcessingState.NEW);
        var vector = new float[com.example.contor.domain.EmailEmbedding.DIMENSIONS];
        emailEmbeddingRepository.save(com.example.contor.domain.EmailEmbedding.builder()
                .id(new com.example.contor.domain.EmailEmbedding.EmailEmbeddingId(embedded.getId().id(), "nomic")).embedding(vector).build());
        emailEmbeddingRepository.save(com.example.contor.domain.EmailEmbedding.builder()
                .id(new com.example.contor.domain.EmailEmbedding.EmailEmbeddingId(embeddedWithOldModel.getId().id(), "old-model")).embedding(vector).build());
        emailMessageRepository.flush();

        assertThat(emailMessageRepository.findIdsWithoutEmbedding(owner.getId(), "nomic", PageRequest.of(0, 10)))
                .containsExactly(recent.getId().id(), embeddedWithOldModel.getId().id(), old.getId().id());
        assertThat(emailMessageRepository.findIdsWithoutEmbedding(owner.getId(), "nomic", PageRequest.of(0, 1))).containsExactly(recent.getId().id());
        assertThat(emailMessageRepository.countIndexable(owner.getId())).isEqualTo(4);
    }
}

