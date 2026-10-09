package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.EmailEmbedding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
}
