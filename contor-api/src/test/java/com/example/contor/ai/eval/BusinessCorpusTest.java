package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.RelationTypeDto;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the committed business mailbox ({@code ai-eval/business}, built by {@code scripts/business-dataset/build.mjs}):
 * every message parses as an eval case, only fictional addresses, labels fit {@link EvalCollections#business()},
 * references point backwards in time, key values are stated in the mail or the mails it depends on, and the expected
 * end state is traceable to messages.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BusinessCorpusTest {

    private static final List<BusinessMessage> MESSAGES = BusinessCorpus.messages();
    private static final Map<String, BusinessMessage> BY_ID = MESSAGES.stream().collect(Collectors.toMap(BusinessMessage::id, m -> m));
    private static final Map<String, CollectionDto> SCHEMA = EvalCollections.business().stream()
            .collect(Collectors.toMap(CollectionDto::getSlug, collection -> collection));
    private static final Pattern ADDRESS = Pattern.compile("[\\w.+-]+@([\\w-]+(?:\\.[\\w-]+)+)");
    private static final Pattern LINK = Pattern.compile("(?:https?://|www\\.)([^\\s/<>\")]+)");
    private static final Set<String> IMPORTANCE = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    @Test
    void the_slice_has_at_least_three_hundred_messages_with_unique_ids_in_time_order() {
        assertThat(MESSAGES).hasSizeGreaterThanOrEqualTo(300);
        assertThat(BY_ID).hasSameSizeAs(MESSAGES);
        assertThat(MESSAGES).extracting(message -> message.email().receivedAt()).isSorted();
    }

    @Test
    void every_message_also_parses_as_a_plain_eval_case() {
        List<EvalCase> cases = EvalCaseLoader.loadClasspath(BusinessCorpus.MAILBOX);

        assertThat(cases).extracting(EvalCase::id).containsExactlyElementsOf(MESSAGES.stream().map(BusinessMessage::id).toList());
        assertThat(cases).allSatisfy(evalCase -> {
            assertThat(evalCase.email().fromAddress()).contains("@");
            assertThat(evalCase.email().body()).isNotBlank();
        });
    }

    @Test
    void addresses_and_links_are_fictional() {
        assertThat(MESSAGES).allSatisfy(message -> {
            String text = message.email().subject() + "\n" + message.email().body();
            List<String> hosts = new ArrayList<>();
            Stream.of(List.of(message.email().fromAddress()), message.email().to(), message.cc()).flatMap(List::stream)
                    .forEach(address -> hosts.add(address.substring(address.indexOf('@') + 1)));
            collect(ADDRESS, text, hosts);
            collect(LINK, text, hosts);
            assertThat(hosts).as("hosts in %s", message.id()).allSatisfy(host -> assertThat(host).endsWith(".example"));
        });
    }

    @Test
    void labels_use_the_business_schema() {
        Map<String, RelationTypeDto> relationTypes = EvalCollections.businessRelationTypes().stream()
                .collect(Collectors.toMap(RelationTypeDto::getSlug, type -> type));
        Map<Long, String> slugById = EvalCollections.business().stream().collect(Collectors.toMap(CollectionDto::getId, CollectionDto::getSlug));
        assertThat(MESSAGES).allSatisfy(message -> {
            message.expected().records().forEach((slug, records) -> {
                assertThat(SCHEMA).as("collection %s in %s", slug, message.id()).containsKey(slug);
                Set<String> fields = SCHEMA.get(slug).getFields().stream().map(CollectionFieldDto::getSlug).collect(Collectors.toSet());
                assertThat(records).allSatisfy(record -> {
                    assertThat(record.title()).isNotBlank();
                    assertThat(fields).as("fields of %s in %s", slug, message.id()).containsAll(record.values().keySet());
                });
            });
            assertThat(message.expected().relations()).allSatisfy(relation -> {
                RelationTypeDto type = relationTypes.get(relation.type());
                assertThat(type).as("relation type %s in %s", relation.type(), message.id()).isNotNull();
                assertThat(relation.from()).startsWith(slugById.get(type.getFromCollectionId()) + ":");
                assertThat(relation.to()).startsWith(slugById.get(type.getToCollectionId()) + ":");
            });
            assertThat(message.mustNotProduce()).allSatisfy(forbidden -> assertThat(SCHEMA).containsKey(forbidden.collection()));
        });
    }

    @Test
    void inbound_mail_is_fully_labelled_and_sent_mail_carries_no_labels() {
        assertThat(MESSAGES).allSatisfy(message -> {
            if (message.sent()) {
                assertThat(message.expected().recordCount()).as(message.id()).isZero();
                assertThat(message.needsReply()).as(message.id()).isNull();
            } else {
                assertThat(message.needsReply()).as("needsReply of %s", message.id()).isNotNull();
                assertThat(message.importance()).as("importance of %s", message.id()).isIn(IMPORTANCE);
            }
        });
    }

    @Test
    void references_point_backwards_and_answers_forwards_in_time() {
        assertThat(MESSAGES).allSatisfy(message -> {
            Instant at = message.email().receivedAt();
            Stream.concat(Stream.ofNullable(message.inReplyTo()), message.dependsOn().stream()).forEach(ref -> {
                assertThat(BY_ID).as("reference %s of %s", ref, message.id()).containsKey(ref);
                assertThat(BY_ID.get(ref).email().receivedAt()).as("%s refers to %s", message.id(), ref).isBefore(at);
            });
            if (message.reply() != null && message.reply().answeredBy() != null) {
                BusinessMessage answer = BY_ID.get(message.reply().answeredBy());
                assertThat(answer).as("answer of %s", message.id()).isNotNull();
                assertThat(answer.sent()).isTrue();
                assertThat(answer.email().receivedAt()).isAfter(at);
            }
        });
    }

    /** Match keys and amounts must be readable from the mail or a mail it depends on (else the label is marked derived). */
    @Test
    void key_values_and_amounts_are_stated_in_the_mail_or_its_dependencies() {
        List<String> missing = new ArrayList<>();
        for (BusinessMessage message : MESSAGES) {
            String text = Stream.concat(Stream.of(message), message.dependsOn().stream().map(BY_ID::get).filter(Objects::nonNull))
                    .map(m -> String.join("\n", m.email().fromAddress(), String.join(" ", m.email().to()), String.join(" ", m.cc()),
                            m.email().subject(), m.email().body()))
                    .collect(Collectors.joining("\n"));
            String compact = alphanumeric(text);
            message.expected().records().forEach((slug, records) -> records.forEach(record -> record.values().forEach((field, value) -> {
                if (message.derived().containsKey(slug + "." + field) || value == null) {
                    return;
                }
                CollectionFieldType type = SCHEMA.get(slug).getFields().stream().filter(f -> f.getSlug().equals(field)).findFirst()
                        .map(CollectionFieldDto::getType).orElse(CollectionFieldType.TEXT);
                boolean key = SCHEMA.get(slug).getMatchKeys().contains(field) && type == CollectionFieldType.TEXT;
                if (key && !compact.contains(alphanumeric(String.valueOf(value)))) {
                    missing.add(message.id() + " " + slug + "." + field + " = " + value);
                }
                if (type == CollectionFieldType.MONEY && value instanceof Map<?, ?> money && !statesAmount(text, money.get("amount"))) {
                    missing.add(message.id() + " " + slug + "." + field + " = " + money.get("amount"));
                }
            })));
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void the_end_state_and_the_insights_point_at_inbound_messages() {
        BusinessCorpus.EndState state = BusinessCorpus.endState();
        assertThat(state.records()).isNotEmpty();
        assertThat(state.records().keySet()).allSatisfy(slug -> assertThat(SCHEMA).containsKey(slug));
        state.records().values().stream().flatMap(List::stream).forEach(record -> assertThat(record.sources()).isNotEmpty()
                .allSatisfy(source -> assertThat(BY_ID.get(source).sent()).as("source %s of %s", source, record.title()).isFalse()));
        assertThat(BusinessCorpus.insights()).isNotEmpty().allSatisfy(insight -> {
            assertThat(insight.evidence()).hasSizeGreaterThanOrEqualTo(2).allSatisfy(id -> assertThat(BY_ID).containsKey(id));
            assertThat(insight.asOf()).isNotNull();
        });
    }

    /** The entity-resolution case of the dataset: one driver, two carriers, and a different Marek who stays separate. */
    @Test
    void the_shared_driver_is_one_person_with_two_carriers() {
        BusinessCorpus.EndState state = BusinessCorpus.endState();
        List<BusinessCorpus.StateRecord> drivers = state.records().get("people").stream()
                .filter(person -> "+48 5550 4171".equals(person.values().get("phone"))).toList();

        assertThat(drivers).hasSize(1);
        assertThat(drivers.getFirst().sources()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(state.relations()).filteredOn(relation -> relation.type().equals("drives_for"))
                .extracting(BusinessCorpus.StateRelation::to).hasSize(2).doesNotHaveDuplicates();
        assertThat(state.records().get("people")).filteredOn(person -> person.title().startsWith("Marek")).hasSize(2);
    }

    private static void collect(Pattern pattern, String text, List<String> hosts) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            hosts.add(matcher.group(1).replaceAll("[.,;:]+$", ""));
        }
    }

    private static String alphanumeric(String text) {
        return Normalizers.text(text).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** An amount in German or English notation, with or without thousands separators (whole amounts also without cents). */
    private static boolean statesAmount(String text, Object amount) {
        String fixed = new BigDecimal(String.valueOf(amount)).setScale(2, RoundingMode.HALF_UP).toPlainString();
        String whole = fixed.substring(0, fixed.indexOf('.'));
        String cents = fixed.substring(fixed.indexOf('.') + 1);
        String grouped = whole.replaceAll("\\B(?=(\\d{3})+(?!\\d))", "#");
        List<String> variants = new ArrayList<>(List.of(grouped.replace("#", ".") + "," + cents, grouped.replace("#", ",") + "." + cents,
                whole + "," + cents, whole + "." + cents, grouped.replace("#", "'") + "." + cents));
        if (cents.equals("00")) {
            variants.addAll(List.of(grouped.replace("#", "."), grouped.replace("#", ","), whole));
        }
        return variants.stream().anyMatch(text::contains);
    }
}
