package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.EmailEmbedding;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class EmailEmbeddingRepositoryTest {

    @Autowired
    private EmailEmbeddingRepository emailEmbeddingRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void can_store_and_load_a_vector_per_model() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var email = emailMessageRepository.save(Fixtures.email(account, "AAMk-1"));
        var vector = new float[EmailEmbedding.DIMENSIONS];
        vector[0] = 0.5f;
        vector[767] = -1f;
        var id = new EmailEmbedding.EmailEmbeddingId(email.getId().id(), "nomic-embed-text");
        emailEmbeddingRepository.saveAndFlush(EmailEmbedding.builder().id(id).embedding(vector).build());

        var found = emailEmbeddingRepository.findById(id).orElseThrow();

        assertThat(found.getEmbedding()).hasSize(768);
        assertThat(found.getEmbedding()[0]).isEqualTo(0.5f);
        assertThat(found.getEmbedding()[767]).isEqualTo(-1f);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(emailEmbeddingRepository.findById_EmailId(email.getId().id())).hasSize(1);
    }

    private EmailEmbedding embedding(com.example.contor.domain.EmailMessage email, String model) {
        var vector = new float[EmailEmbedding.DIMENSIONS];
        vector[1] = 1f;
        return EmailEmbedding.builder().id(new EmailEmbedding.EmailEmbeddingId(email.getId().id(), model)).embedding(vector).build();
    }

    @Test
    void counts_the_indexed_mail_of_one_owner_for_one_model_and_ignores_sent_mail() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var first = emailMessageRepository.save(Fixtures.email(mine, "m1"));
        var second = emailMessageRepository.save(Fixtures.email(mine, "m2"));
        var sent = Fixtures.email(mine, "m3");
        sent.setFolder("sent");
        sent = emailMessageRepository.save(sent);
        var foreign = emailMessageRepository.save(Fixtures.email(theirs, "m4"));
        emailEmbeddingRepository.saveAll(java.util.List.of(embedding(first, "nomic"), embedding(second, "old-model"), embedding(sent, "nomic"),
                embedding(foreign, "nomic")));
        emailEmbeddingRepository.flush();

        assertThat(emailEmbeddingRepository.countIndexed(owner.getId(), "nomic")).isEqualTo(1);
        assertThat(emailEmbeddingRepository.countIndexed(owner.getId(), "old-model")).isEqualTo(1);
    }

    @Test
    void deletes_the_vectors_of_every_other_model() {
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var email = emailMessageRepository.save(Fixtures.email(account, "m1"));
        emailEmbeddingRepository.saveAll(java.util.List.of(embedding(email, "nomic"), embedding(email, "old-model"), embedding(email, "older")));
        emailEmbeddingRepository.flush();

        emailEmbeddingRepository.deleteById_ModelNot("nomic");
        emailEmbeddingRepository.flush();

        assertThat(emailEmbeddingRepository.findById_EmailId(email.getId().id())).extracting(found -> found.getId().model()).containsExactly("nomic");
    }
    private EmailMessage received(MailAccount account, String id, String receivedAt) {
        var email = Fixtures.email(account, id);
        email.setReceivedAt(Instant.parse(receivedAt));
        return emailMessageRepository.save(email);
    }

    /** A unit vector pointing mostly along the first axis, tilted by {@code tilt} along the second. */
    private EmailEmbedding vector(EmailMessage email, String model, float tilt) {
        var vector = new float[EmailEmbedding.DIMENSIONS];
        vector[0] = 1f;
        vector[1] = tilt;
        return EmailEmbedding.builder().id(new EmailEmbedding.EmailEmbeddingId(email.getId().id(), model)).embedding(vector).build();
    }

    @Test
    void finds_the_nearest_inbox_mail_of_the_same_owner_closest_first() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var theirs = mailAccountRepository.save(Fixtures.mailAccount(other, "them@example.com"));
        var query = received(mine, "q", "2026-06-01T10:00:00Z");
        var closest = received(mine, "a", "2026-05-01T10:00:00Z");
        var close = received(mine, "b", "2026-05-02T10:00:00Z");
        var far = received(mine, "c", "2026-05-03T10:00:00Z");
        var old = received(mine, "d", "2025-01-01T10:00:00Z");
        var sent = Fixtures.email(mine, "e");
        sent.setFolder("sent");
        sent = emailMessageRepository.save(sent);
        var foreign = received(theirs, "f", "2026-05-04T10:00:00Z");
        var otherModel = received(mine, "g", "2026-05-05T10:00:00Z");
        emailEmbeddingRepository.saveAll(List.of(vector(query, "nomic", 0f), vector(closest, "nomic", 0.1f), vector(close, "nomic", 0.3f),
                vector(far, "nomic", 5f), vector(old, "nomic", 0f), vector(sent, "nomic", 0f), vector(foreign, "nomic", 0f),
                vector(otherModel, "old-model", 0f)));
        emailEmbeddingRepository.flush();

        var nearest = emailEmbeddingRepository.findNearest(owner.getId(), query.getId().id(), "nomic",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-01T10:00:00Z"), 0.25, 10);

        assertThat(nearest).extracting(NearEmail::emailId).containsExactly(closest.getId().id(), close.getId().id());
        assertThat(nearest.getFirst().distance()).isLessThan(nearest.get(1).distance());
        assertThat(emailEmbeddingRepository.findNearest(owner.getId(), query.getId().id(), "nomic",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-06-01T10:00:00Z"), 0.25, 1)).hasSize(1);
    }

    @Test
    void finds_nothing_for_an_email_without_a_vector_or_of_another_owner() {
        var owner = appUserRepository.save(Fixtures.user("owner"));
        var other = appUserRepository.save(Fixtures.user("other"));
        var mine = mailAccountRepository.save(Fixtures.mailAccount(owner, "me@example.com"));
        var query = received(mine, "q", "2026-06-01T10:00:00Z");
        var neighbour = received(mine, "a", "2026-05-01T10:00:00Z");
        emailEmbeddingRepository.saveAndFlush(vector(neighbour, "nomic", 0f));

        assertThat(emailEmbeddingRepository.findNearest(owner.getId(), query.getId().id(), "nomic", Instant.EPOCH, Instant.parse("2027-01-01T00:00:00Z"), 1.0, 5)).isEmpty();
        emailEmbeddingRepository.saveAndFlush(vector(query, "nomic", 0f));
        assertThat(emailEmbeddingRepository.findNearest(other.getId(), query.getId().id(), "nomic", Instant.EPOCH, Instant.parse("2027-01-01T00:00:00Z"), 1.0, 5)).isEmpty();
    }
}
