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

/** Guards the multi-mail cases ({@code ai-eval/context}): each parses, has earlier emails, and uses the eval schema. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ContextCorpusTest {

    private static final List<EvalCase> CASES = EvalCaseLoader.loadClasspath("ai-eval/context");
    private static final Map<String, CollectionDto> SCHEMA = EvalCollections.all().stream()
            .collect(Collectors.toMap(CollectionDto::getSlug, collection -> collection));

    @Test
    void every_case_has_earlier_emails_received_before_its_email() {
        assertThat(CASES).hasSizeGreaterThanOrEqualTo(3);
        assertThat(CASES).allSatisfy(evalCase -> {
            EvalCase.Context context = evalCase.context();
            assertThat(context).as(evalCase.id()).isNotNull();
            assertThat(context.thread().size() + context.fromSender().size() + context.related().size()).as(evalCase.id()).isPositive();
            assertThat(context.thread()).allSatisfy(email -> assertThat(email.receivedAt()).isBefore(evalCase.email().receivedAt()));
            assertThat(context.fromSender()).allSatisfy(email -> assertThat(email.receivedAt()).isBefore(evalCase.email().receivedAt()));
            assertThat(context.related()).allSatisfy(email -> assertThat(email.receivedAt()).isBefore(evalCase.email().receivedAt()));
        });
    }

    @Test
    void expected_records_use_existing_collections_and_fields() {
        assertThat(CASES).allSatisfy(evalCase -> evalCase.expected().records().forEach((slug, records) -> {
            assertThat(SCHEMA).as("collection %s in %s", slug, evalCase.id()).containsKey(slug);
            Set<String> fields = SCHEMA.get(slug).getFields().stream().map(field -> field.getSlug()).collect(Collectors.toSet());
            assertThat(records).allSatisfy(record -> assertThat(fields).containsAll(record.values().keySet()));
        }));
    }
}
