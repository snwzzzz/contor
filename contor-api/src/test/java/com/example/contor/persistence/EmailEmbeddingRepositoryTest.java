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
}
