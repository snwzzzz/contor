package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

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
    void the_business_schema_adds_the_wholesale_collections_to_the_system_ones() {
        List<CollectionDto> business = EvalCollections.business();

        assertThat(business).extracting(CollectionDto::getSlug).containsExactly("people", "organizations", "events", "tasks", "customers",
                "suppliers", "sales_orders", "purchase_orders", "shipments", "complaints", "invoices");
        assertThat(business).extracting(CollectionDto::getId).doesNotHaveDuplicates().doesNotContainNull();
        CollectionDto shipments = business.stream().filter(c -> c.getSlug().equals("shipments")).findFirst().orElseThrow();
        assertThat(shipments.getMatchKeys()).containsExactly("tracking_number");
        assertThat(shipments.getFields()).extracting(field -> field.getSlug())
                .containsExactly("tracking_number", "carrier", "direction", "order_ref", "state", "eta", "driver");
        CollectionDto invoices = business.stream().filter(c -> c.getSlug().equals("invoices")).findFirst().orElseThrow();
        assertThat(invoices.getFields()).extracting(field -> field.getSlug()).contains("direction");
    }

    @Test
    void business_relation_types_connect_shipments_orders_and_drivers() {
        var ids = EvalCollections.business().stream().collect(Collectors.toMap(c -> c.getSlug(), c -> c.getId()));
        var types = EvalCollections.businessRelationTypes().stream()
                .collect(Collectors.toMap(type -> type.getSlug(), type -> type));

        assertThat(EvalCollections.businessRelationTypes()).extracting(type -> type.getSlug())
                .containsExactly("works_at", "knows", "attends", "supplies", "ships", "delivers", "drives_for");
        assertThat(types.get("ships").getFromCollectionId()).isEqualTo(ids.get("shipments"));
        assertThat(types.get("ships").getToCollectionId()).isEqualTo(ids.get("sales_orders"));
        assertThat(types.get("delivers").getToCollectionId()).isEqualTo(ids.get("purchase_orders"));
        assertThat(types.get("drives_for").getFromCollectionId()).isEqualTo(ids.get("people"));
        assertThat(types.get("drives_for").getToCollectionId()).isEqualTo(ids.get("organizations"));
        assertThat(EvalCollections.businessRelationTypes()).extracting(type -> type.getId()).doesNotHaveDuplicates();
    }

    @Test
    void relation_types_connect_the_seeded_collections() {
        var collections = EvalCollections.all().stream().collect(Collectors.toMap(c -> c.getSlug(), c -> c.getId()));

        assertThat(EvalCollections.relationTypes()).extracting(type -> type.getSlug()).containsExactly("works_at", "knows", "attends");
        assertThat(EvalCollections.relationTypes().getFirst().getFromCollectionId()).isEqualTo(collections.get("people"));
        assertThat(EvalCollections.relationTypes().getFirst().getToCollectionId()).isEqualTo(collections.get("organizations"));
        assertThat(EvalCollections.relationTypes().get(1).getDirected()).isFalse();
    }
}
