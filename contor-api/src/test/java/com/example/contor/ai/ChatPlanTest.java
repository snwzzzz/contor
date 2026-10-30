package com.example.contor.ai;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class ChatPlanTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> SLUGS = List.of("people", "complaints");

    private static ChatPlan parse(String json) {
        return ChatPlan.parse(JSON.readTree(json), SLUGS);
    }

    @Test
    void reads_the_searches_and_collections() {
        var plan = parse("{\"searches\": [\"Anna Kessler\", \"SO-26-0155\"], \"collections\": [\"complaints\"]}");

        assertThat(plan.searches()).containsExactly("Anna Kessler", "SO-26-0155");
        assertThat(plan.collections()).containsExactly("complaints");
    }

    @Test
    void trims_and_drops_empty_and_repeated_searches() {
        var plan = parse("{\"searches\": [\"  Anna  \", \"\", \"anna\", \" \", \"Kessler GmbH\"]}");

        assertThat(plan.searches()).containsExactly("Anna", "Kessler GmbH");
    }

    @Test
    void keeps_at_most_five_short_searches() {
        var plan = parse("{\"searches\": [\"a1\", \"a2\", \"a3\", \"a4\", \"a5\", \"a6\", \"" + "x".repeat(150) + "\"]}");

        assertThat(plan.searches()).containsExactly("a1", "a2", "a3", "a4", "a5");
    }

    @Test
    void drops_searches_too_long_to_be_names() {
        assertThat(parse("{\"searches\": [\"" + "x".repeat(ChatPlan.MAX_SEARCH_LENGTH + 1) + "\"]}").searches()).isEmpty();
    }

    @Test
    void drops_unknown_collections_and_keeps_at_most_two() {
        var plan = ChatPlan.parse(JSON.readTree("{\"collections\": [\"orders\", \"People\", \"complaints\", \"invoices\"]}"),
                List.of("people", "complaints", "invoices"));

        assertThat(plan.collections()).containsExactly("people", "complaints");
    }

    @Test
    void a_missing_or_odd_answer_is_an_empty_plan() {
        assertThat(parse("{}").isEmpty()).isTrue();
        assertThat(parse("{\"searches\": \"Anna\", \"collections\": 3}").isEmpty()).isTrue();
    }
}
