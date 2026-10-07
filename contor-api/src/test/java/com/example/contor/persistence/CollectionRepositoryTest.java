package com.example.contor.persistence;

import com.example.contor.RepositoryTest;
import com.example.contor.domain.CollectionSchemaChange;
import com.example.contor.domain.DisplayConfig;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
public class CollectionRepositoryTest {

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private CollectionFieldRepository collectionFieldRepository;

    @Autowired
    private CollectionSchemaChangeRepository collectionSchemaChangeRepository;

    @Test
    void can_save_and_find_by_slug_with_json_config() {
        var collection = Fixtures.collection("deliveries");
        collection.setMatchKeys(List.of("tracking_number"));
        collection.setDisplayConfig(new DisplayConfig("table", "status", "eta", "title"));
        collectionRepository.saveAndFlush(collection);

        var found = collectionRepository.findBySlug(new Slug("deliveries"));

        assertThat(found).isPresent();
        assertThat(found.get().getSchemaVersion()).isEqualTo(1);
        assertThat(found.get().isSystem()).isFalse();
        assertThat(found.get().getMatchKeys()).containsExactly("tracking_number");
        assertThat(found.get().getDisplayConfig().kanbanStatusField()).isEqualTo("status");
        assertThat(collectionRepository.existsByTableName("c_deliveries")).isTrue();
        assertThat(collectionRepository.existsByTableName("c_orders")).isFalse();
    }

    @Test
    void finds_fields_of_a_collection_in_position_order() {
        var collection = collectionRepository.save(Fixtures.collection("deliveries"));
        collectionFieldRepository.save(Fixtures.field(collection, "eta", FieldType.DATE, 2));
        collectionFieldRepository.save(Fixtures.field(collection, "carrier", FieldType.TEXT, 1));

        var fields = collectionFieldRepository.findByCollection_IdOrderByPositionAsc(collection.getId());

        assertThat(fields).extracting(f -> f.getSlug().value()).containsExactly("carrier", "eta");
        assertThat(fields.get(0).isRequired()).isFalse();
        assertThat(collectionFieldRepository.existsByCollection_IdAndSlug(collection.getId(), new Slug("eta"))).isTrue();
    }

    @Test
    void field_slug_is_unique_per_collection() {
        var deliveries = collectionRepository.save(Fixtures.collection("deliveries"));
        var orders = collectionRepository.save(Fixtures.collection("orders"));
        collectionFieldRepository.saveAndFlush(Fixtures.field(deliveries, "status_note", FieldType.TEXT, 1));
        collectionFieldRepository.saveAndFlush(Fixtures.field(orders, "status_note", FieldType.TEXT, 1));

        assertThatThrownBy(() -> collectionFieldRepository.saveAndFlush(Fixtures.field(deliveries, "status_note", FieldType.TEXT, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keeps_schema_change_history_in_version_order() {
        var collection = collectionRepository.save(Fixtures.collection("deliveries"));
        collectionSchemaChangeRepository.save(CollectionSchemaChange.builder().collection(collection)
                .fromVersion(1).toVersion(2).change(Map.of("op", "ADD_FIELD")).ddlExecuted("alter table ...").executedAt(Instant.now()).build());
        collectionSchemaChangeRepository.save(CollectionSchemaChange.builder().collection(collection)
                .fromVersion(0).toVersion(1).change(Map.of("op", "CREATE")).ddlExecuted("create table ...").executedAt(Instant.now()).build());

        var history = collectionSchemaChangeRepository.findByCollection_IdOrderByToVersionAsc(collection.getId());

        assertThat(history).extracting(CollectionSchemaChange::getToVersion).containsExactly(1, 2);
        assertThat(history.get(0).getChange()).containsEntry("op", "CREATE");
    }
}
