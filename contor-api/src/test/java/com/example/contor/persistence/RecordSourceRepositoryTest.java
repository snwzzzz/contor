package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.ExternalApiItem;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.RecordSourceRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private ExternalApiRepository externalApiRepository;

    @Autowired
    private ExternalApiEndpointRepository externalApiEndpointRepository;

    @Autowired
    private ExternalApiItemRepository externalApiItemRepository;

    private ExternalApiItem apiItem(String key) {
        var api = externalApiRepository.save(Fixtures.externalApi(appUserRepository.save(Fixtures.user("api-owner")), "shop"));
        return externalApiItemRepository.save(Fixtures.apiItem(externalApiEndpointRepository.save(Fixtures.endpoint(api, "orders")), key));
    }

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

    @Test
    void a_record_can_come_from_an_api_item_instead_of_an_email() {
        var orders = collectionRepository.save(Fixtures.collection("orders"));
        var item = apiItem("SO-1");
        recordSourceRepository.saveAndFlush(RecordSource.builder().collection(orders).recordId(1L).apiItem(item).role(RecordSourceRole.CREATED_FROM).build());

        assertThat(recordSourceRepository.findByCollection_IdAndRecordId(orders.getId(), 1L)).singleElement().satisfies(source -> {
            assertThat(source.getEmail()).isNull();
            assertThat(source.getApiItem().getItemKey()).isEqualTo("SO-1");
        });
        assertThat(recordSourceRepository.findByApiItem_Id(item.getId())).hasSize(1);
    }

    @Test
    void a_source_without_an_origin_is_rejected() {
        var orders = collectionRepository.save(Fixtures.collection("orders"));

        assertThatThrownBy(() -> recordSourceRepository.saveAndFlush(RecordSource.builder().collection(orders).recordId(1L)
                .role(RecordSourceRole.CREATED_FROM).build()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_record_source_origin");
    }

    @Test
    void a_source_with_both_an_email_and_an_api_item_is_rejected() {
        var orders = collectionRepository.save(Fixtures.collection("orders"));
        var item = apiItem("SO-1");
        var account = mailAccountRepository.save(Fixtures.mailAccount(appUserRepository.save(Fixtures.user("owner")), "me@example.com"));
        var email = emailMessageRepository.save(Fixtures.email(account, "AAMk-1"));

        assertThatThrownBy(() -> recordSourceRepository.saveAndFlush(RecordSource.builder().collection(orders).recordId(1L).email(email)
                .apiItem(item).role(RecordSourceRole.CREATED_FROM).build()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_record_source_origin");
    }

    @Test
    void an_api_item_is_linked_to_a_record_once_per_role() {
        var orders = collectionRepository.save(Fixtures.collection("orders"));
        var item = apiItem("SO-1");
        recordSourceRepository.saveAndFlush(RecordSource.builder().collection(orders).recordId(1L).apiItem(item).role(RecordSourceRole.CREATED_FROM).build());

        assertThatThrownBy(() -> recordSourceRepository.saveAndFlush(RecordSource.builder().collection(orders).recordId(1L).apiItem(item)
                .role(RecordSourceRole.CREATED_FROM).build()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_record_source_record_item_role");
    }
}
