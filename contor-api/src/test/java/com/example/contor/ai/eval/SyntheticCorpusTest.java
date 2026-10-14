package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionDto;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the committed synthetic corpus: every case parses and only refers to collections and fields of the eval schema. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SyntheticCorpusTest {

    private static final List<EvalCase> CASES = EvalCaseLoader.loadClasspath("ai-eval/synthetic");
    private static final Map<String, CollectionDto> SCHEMA = EvalCollections.all().stream()
            .collect(Collectors.toMap(CollectionDto::getSlug, collection -> collection));

    @Test
    void the_corpus_has_forty_to_sixty_cases_with_unique_ids() {
        assertThat(CASES).hasSizeBetween(40, 60);
        assertThat(CASES.stream().map(EvalCase::id).collect(Collectors.toSet())).hasSameSizeAs(CASES);
    }

    @Test
    void every_email_is_complete() {
        assertThat(CASES).allSatisfy(evalCase -> {
            assertThat(evalCase.email().mailbox()).isNotBlank();
            assertThat(evalCase.email().fromAddress()).contains("@");
            assertThat(evalCase.email().subject()).isNotBlank();
            assertThat(evalCase.email().body()).isNotBlank();
            assertThat(evalCase.email().receivedAt()).isNotNull();
        });
    }

    @Test
    void expected_records_use_existing_collections_and_fields() {
        assertThat(CASES).allSatisfy(evalCase -> evalCase.expected().records().forEach((slug, records) -> {
            assertThat(SCHEMA).as("collection %s in %s", slug, evalCase.id()).containsKey(slug);
            Set<String> fields = SCHEMA.get(slug).getFields().stream().map(field -> field.getSlug()).collect(Collectors.toSet());
            assertThat(records).allSatisfy(record -> {
                assertThat(record.title()).isNotBlank();
                assertThat(fields).as("fields of %s in %s", slug, evalCase.id()).containsAll(record.values().keySet());
            });
        }));
    }

    @Test
    void forbidden_entries_and_relations_refer_to_the_schema() {
        assertThat(CASES).allSatisfy(evalCase -> {
            assertThat(evalCase.mustNotProduce()).allSatisfy(forbidden -> assertThat(SCHEMA).containsKey(forbidden.collection()));
            assertThat(evalCase.expected().relations()).allSatisfy(relation -> {
                assertThat(relation.type()).isIn("works_at", "knows", "attends");
                assertThat(relation.from()).matches("[a-z_]+:.+");
                assertThat(relation.to()).matches("[a-z_]+:.+");
            });
        });
    }

    @Test
    void the_corpus_contains_mails_that_must_produce_nothing_and_both_languages() {
        long nothing = CASES.stream().filter(evalCase -> evalCase.expected().recordCount() == 0).count();
        long german = CASES.stream().filter(evalCase -> evalCase.id().contains("-de-")).count();

        assertThat(nothing).isGreaterThanOrEqualTo(6);
        assertThat(german).isGreaterThanOrEqualTo(10);
        assertThat(CASES.size() - german).isGreaterThanOrEqualTo(10);
    }
}
