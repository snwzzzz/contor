package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EvalCollectionsTest {

    @Test
    void contains_the_system_collections_and_the_fixture_categories_with_unique_ids() {
        List<CollectionDto> all = EvalCollections.all();

        assertThat(all).extracting(CollectionDto::getSlug)
                .containsExactly("people", "organizations", "events", "tasks", "orders", "invoices", "subscriptions", "applications");
        assertThat(all).extracting(CollectionDto::getId).doesNotHaveDuplicates().doesNotContainNull();
    }

    @Test
    void fields_carry_type_required_flag_and_options() {
        CollectionDto tasks = EvalCollections.all().stream().filter(c -> c.getSlug().equals("tasks")).findFirst().orElseThrow();

        assertThat(tasks.getFields()).anySatisfy(field -> {
            assertThat(field.getSlug()).isEqualTo("state");
            assertThat(field.getType()).isEqualTo(CollectionFieldType.STATUS);
            assertThat(field.getOptions()).containsKey("states");
        });
        assertThat(tasks.getFields()).allSatisfy(field -> assertThat(field.getArchivedAt()).isNull());
    }

    @Test
    void match_keys_are_kept() {
        assertThat(EvalCollections.all().stream().filter(c -> c.getSlug().equals("orders")).findFirst().orElseThrow().getMatchKeys())
                .containsExactly("order_number");
    }

    @Test
    void relation_types_connect_the_seeded_collections() {
        var collections = EvalCollections.all().stream().collect(java.util.stream.Collectors.toMap(c -> c.getSlug(), c -> c.getId()));

        assertThat(EvalCollections.relationTypes()).extracting(type -> type.getSlug()).containsExactly("works_at", "knows", "attends");
        assertThat(EvalCollections.relationTypes().getFirst().getFromCollectionId()).isEqualTo(collections.get("people"));
        assertThat(EvalCollections.relationTypes().getFirst().getToCollectionId()).isEqualTo(collections.get("organizations"));
        assertThat(EvalCollections.relationTypes().get(1).getDirected()).isFalse();
    }
}
