package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CollectionPlanTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void reads_the_analyses_the_model_asked_for() {
        var plan = CollectionPlan.parse(JSON.readTree("""
                {"analyses": [{"title": " Top customers ", "collection": "Sales_Orders", "groupBy": " customer ",
                  "measure": "SUM:total", "order": "desc", "limit": 5, "filters": ["state:ne:cancelled", " "]}]}"""));

        assertThat(plan.analyses()).singleElement().isEqualTo(new CollectionPlan.Analysis("Top customers", "sales_orders", "customer",
                "sum:total", false, 5, List.of("state:ne:cancelled")));
    }

    @Test
    void fills_in_defaults_and_clamps_the_limit() {
        var plan = CollectionPlan.parse(JSON.readTree("""
                {"analyses": [{"collection": "complaints"}, {"collection": "orders", "order": "asc", "limit": 500, "measure": ""},
                  {"collection": "orders", "limit": -3}]}"""));

        assertThat(plan.analyses()).containsExactly(
                new CollectionPlan.Analysis("", "complaints", "", "count", false, CollectionPlan.DEFAULT_LIMIT, List.of()),
                new CollectionPlan.Analysis("", "orders", "", "count", true, CollectionPlan.MAX_LIMIT, List.of()),
                new CollectionPlan.Analysis("", "orders", "", "count", false, 1, List.of()));
    }

    @Test
    void drops_analyses_without_a_collection_and_keeps_at_most_three() {
        var plan = CollectionPlan.parse(JSON.readTree("""
                {"analyses": [{"collection": " "}, {"collection": "a"}, {"collection": "b"}, {"collection": "c"}, {"collection": "d"}]}"""));

        assertThat(plan.analyses()).extracting(CollectionPlan.Analysis::collection).containsExactly("a", "b", "c");
    }

    @Test
    void an_answer_without_analyses_is_an_empty_plan() {
        assertThat(CollectionPlan.parse(JSON.readTree("{}")).analyses()).isEmpty();
        assertThat(CollectionPlan.parse(JSON.readTree("{\"analyses\": \"none\"}")).analyses()).isEmpty();
    }

    @Test
    void the_schema_offers_only_the_known_collections() {
        Map<String, Object> schema = CollectionPlan.schema(List.of("customers", "sales_orders"));

        assertThat(schema.toString()).contains("enum=[customers, sales_orders]", "enum=[desc, asc]");
    }
}
