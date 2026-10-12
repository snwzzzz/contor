package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
public class RecordSourceRepositoryTest {

    @Autowired
    private RecordSourceRepository recordSourceRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void finds_sources_by_collection_and_record_pair() {
        var deliveries = collectionRepository.save(Fixtures.collection("deliveries"));
        var orders = collectionRepository.save(Fixtures.collection("orders"));
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var email = emailMessageRepository.save(Fixtures.email(account, "AAMk-1"));
        recordSourceRepository.save(RecordSource.builder().collection(deliveries).recordId(1L).email(email).role(RecordSourceRole.CREATED_FROM).build());
        recordSourceRepository.save(RecordSource.builder().collection(orders).recordId(1L).email(email).role(RecordSourceRole.MENTIONED_IN).build());

        var sources = recordSourceRepository.findByCollection_IdAndRecordId(deliveries.getId(), 1L);

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getRole()).isEqualTo(RecordSourceRole.CREATED_FROM);
        assertThat(sources.get(0).getCreatedAt()).isNotNull();
        assertThat(recordSourceRepository.findByEmail_Id(email.getId())).hasSize(2);
    }

    @Test
    void finds_the_sources_of_several_emails_at_once() {
        var orders = collectionRepository.save(Fixtures.collection("orders"));
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var first = emailMessageRepository.save(Fixtures.email(account, "AAMk-1"));
        var second = emailMessageRepository.save(Fixtures.email(account, "AAMk-2"));
        var other = emailMessageRepository.save(Fixtures.email(account, "AAMk-3"));
        recordSourceRepository.save(RecordSource.builder().collection(orders).recordId(1L).email(first).role(RecordSourceRole.CREATED_FROM).build());
        recordSourceRepository.save(RecordSource.builder().collection(orders).recordId(2L).email(second).role(RecordSourceRole.CREATED_FROM).build());
        recordSourceRepository.save(RecordSource.builder().collection(orders).recordId(3L).email(other).role(RecordSourceRole.CREATED_FROM).build());

        var sources = recordSourceRepository.findByEmail_IdIn(List.of(first.getId(), second.getId()));

        assertThat(sources).extracting(RecordSource::getRecordId).containsExactlyInAnyOrder(1L, 2L);
    }
}
