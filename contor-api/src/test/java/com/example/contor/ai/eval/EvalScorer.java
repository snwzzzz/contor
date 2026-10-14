package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldDto;
import com.example.contor.api.model.CollectionFieldType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Scores the extraction output against the labelled cases.
 * <p>
 * Matching, per collection and case, one-to-one and best pair first: records of a collection with match keys match when
 * all keys are present on both sides and equal (normalized); a key mismatch never matches. Everything else matches by
 * title similarity (Jaccard over word tokens, at least {@value #TITLE_SIMILARITY}), because the model words titles
 * freely ("Send CV to Acme" vs "Send CV").
 */
final class EvalScorer {

    static final double TITLE_SIMILARITY = 0.5;
    private static final double LENIENT_OVERLAP = 0.15;
    private static final double KEY_MATCH = 2.0;
    private static final Set<String> DATE_COLLECTIONS = Set.of("events", "tasks");
    private static final Set<CollectionFieldType> DATE_TYPES = Set.of(CollectionFieldType.DATE, CollectionFieldType.DATETIME);

    private final Map<String, CollectionDto> collections = new HashMap<>();
    private final Set<String> undirectedRelationTypes;
    private final boolean lenientTitles;

    EvalScorer(List<CollectionDto> collections) {
        this(collections, Set.of());
    }

    /** @param undirectedRelationTypes slugs of relation types that hold in both directions (e.g. {@code knows}) */
    EvalScorer(List<CollectionDto> collections, Set<String> undirectedRelationTypes) {
        this(collections, undirectedRelationTypes, false);
    }

    /**
     * @param lenientTitles records without match keys also match when their titles share a reference number (e.g. the
     *                      "1088" of PO-26-1088) or, with at least a little title overlap, the same date value — for
     *                      free-text titles such as to-dos written in another language or word order (business eval)
     */
    EvalScorer(List<CollectionDto> collections, Set<String> undirectedRelationTypes, boolean lenientTitles) {
        collections.forEach(collection -> this.collections.put(collection.getSlug(), collection));
        this.undirectedRelationTypes = undirectedRelationTypes;
        this.lenientTitles = lenientTitles;
    }

    EvalReport score(String model, String promptVersion, List<EvalCase> cases, List<CaseResult> results) {
        Map<String, CaseResult> resultById = new HashMap<>();
        results.forEach(result -> resultById.put(result.caseId(), result));

        Map<String, int[]> perCollection = new TreeMap<>(); // expected, produced, true positives
        Map<String, int[]> perField = new TreeMap<>();      // correct, total
        int[] dates = new int[2];
        int[] relations = new int[3]; // expected, produced, true positives
        int nothingCases = 0;
        int nothingWithOutput = 0;
        int forbiddenHits = 0;
        int failed = 0;
        List<Long> latencies = new ArrayList<>();

        for (EvalCase evalCase : cases) {
            CaseResult result = Optional.ofNullable(resultById.get(evalCase.id())).orElse(new CaseResult(evalCase.id(), List.of(), 0, "no result"));
            if (result.error() != null) {
                failed++;
            } else {
                latencies.add(result.latencyMillis());
            }
            if (evalCase.expected().recordCount() == 0) {
                nothingCases++;
                if (!result.produced().isEmpty()) {
                    nothingWithOutput++;
                }
            }
            forbiddenHits += forbiddenHits(evalCase, result);

            Map<String, Map<Integer, Integer>> pairsBySlug = new HashMap<>();
            Map<String, List<ProducedRecord>> producedBySlug = new HashMap<>();
            Set<String> slugs = new TreeSet<>(evalCase.expected().records().keySet());
            result.produced().forEach(record -> slugs.add(record.collection()));
            for (String slug : slugs) {
                List<EvalCase.ExpectedRecord> expected = evalCase.expected().records().getOrDefault(slug, List.of());
                List<ProducedRecord> produced = result.produced().stream().filter(record -> slug.equals(record.collection())).toList();
                Map<Integer, Integer> pairs = match(collections.get(slug), expected, produced);
                pairsBySlug.put(slug, pairs);
                producedBySlug.put(slug, produced);

                int[] counts = perCollection.computeIfAbsent(slug, key -> new int[3]);
                counts[0] += expected.size();
                counts[1] += produced.size();
                counts[2] += pairs.size();

                for (int i = 0; i < expected.size(); i++) {
                    ProducedRecord match = pairs.containsKey(i) ? produced.get(pairs.get(i)) : null;
                    scoreFields(slug, expected.get(i), match, perField, dates);
                }
            }
            scoreRelations(evalCase, result, pairsBySlug, producedBySlug, relations);
        }

        Map<String, EvalReport.CollectionScore> collectionScores = new LinkedHashMap<>();
        perCollection.forEach((slug, c) -> collectionScores.put(slug,
                new EvalReport.CollectionScore(c[0], c[1], c[2], ratio(c[2], c[1]), ratio(c[2], c[0]))));
        Map<String, EvalReport.FieldScore> fieldScores = new LinkedHashMap<>();
        int[] overall = new int[2];
        perField.forEach((name, f) -> {
            fieldScores.put(name, fieldScore(f));
            overall[0] += f[0];
            overall[1] += f[1];
        });
        return new EvalReport(model, promptVersion, cases.size(), failed, collectionScores, fieldScores, fieldScore(overall),
                fieldScore(dates), new EvalReport.FalsePositives(nothingCases, nothingWithOutput, ratio(nothingWithOutput, nothingCases)),
                forbiddenHits, latency(latencies),
                new EvalReport.CollectionScore(relations[0], relations[1], relations[2], ratio(relations[2], relations[1]), ratio(relations[2], relations[0])));
    }

    /**
     * An expected relation is found when both of its records were matched with a produced record and a produced
     * relation of the same type connects exactly those produced records (either way round for undirected types).
     */
    private void scoreRelations(EvalCase evalCase, CaseResult result, Map<String, Map<Integer, Integer>> pairsBySlug,
                                Map<String, List<ProducedRecord>> producedBySlug, int[] counts) {
        List<ProducedRelation> unclaimed = new ArrayList<>(result.producedRelations());
        counts[0] += evalCase.expected().relations().size();
        counts[1] += result.producedRelations().size();
        for (EvalCase.ExpectedRelation expected : evalCase.expected().relations()) {
            ProducedRecord from = producedFor(evalCase, expected.from(), pairsBySlug, producedBySlug);
            ProducedRecord to = producedFor(evalCase, expected.to(), pairsBySlug, producedBySlug);
            if (from == null || to == null) {
                continue;
            }
            boolean undirected = undirectedRelationTypes.contains(expected.type());
            ProducedRelation found = unclaimed.stream()
                    .filter(relation -> relation.type().equals(expected.type()))
                    .filter(relation -> connects(relation, from, to) || (undirected && connects(relation, to, from)))
                    .findFirst().orElse(null);
            if (found != null) {
                unclaimed.remove(found);
                counts[2]++;
            }
        }
    }

    private static boolean connects(ProducedRelation relation, ProducedRecord from, ProducedRecord to) {
        return relation.fromCollection().equals(from.collection()) && Normalizers.text(relation.fromTitle()).equals(Normalizers.text(from.title()))
                && relation.toCollection().equals(to.collection()) && Normalizers.text(relation.toTitle()).equals(Normalizers.text(to.title()));
    }

    /** The produced record matched with the expected record named {@code "<slug>:<title>"}, or {@code null}. */
    private static ProducedRecord producedFor(EvalCase evalCase, String endpoint, Map<String, Map<Integer, Integer>> pairsBySlug,
                                              Map<String, List<ProducedRecord>> producedBySlug) {
        int colon = endpoint.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String slug = endpoint.substring(0, colon);
        String title = Normalizers.text(endpoint.substring(colon + 1));
        List<EvalCase.ExpectedRecord> expected = evalCase.expected().records().getOrDefault(slug, List.of());
        for (int i = 0; i < expected.size(); i++) {
            if (Normalizers.text(expected.get(i).title()).equals(title)) {
                Integer produced = pairsBySlug.getOrDefault(slug, Map.of()).get(i);
                return produced != null ? producedBySlug.get(slug).get(produced) : null;
            }
        }
        return null;
    }

    /** Indexes of the expected records mapped to the index of their produced record. */
    private Map<Integer, Integer> match(CollectionDto collection, List<EvalCase.ExpectedRecord> expected, List<ProducedRecord> produced) {
        record Candidate(int expected, int produced, double score) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int e = 0; e < expected.size(); e++) {
            for (int p = 0; p < produced.size(); p++) {
                double score = matchScore(collection, expected.get(e), produced.get(p));
                if (score >= TITLE_SIMILARITY) {
                    candidates.add(new Candidate(e, p, score));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        Map<Integer, Integer> pairs = new HashMap<>();
        Set<Integer> usedProduced = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!pairs.containsKey(candidate.expected()) && usedProduced.add(candidate.produced())) {
                pairs.put(candidate.expected(), candidate.produced());
            }
        }
        return pairs;
    }

    /** {@value #KEY_MATCH} for equal match keys, -1 for different ones, else the title similarity. */
    private double matchScore(CollectionDto collection, EvalCase.ExpectedRecord expected, ProducedRecord produced) {
        List<String> keys = collection != null && collection.getMatchKeys() != null ? collection.getMatchKeys() : List.of();
        boolean keysPresent = !keys.isEmpty() && keys.stream()
                .allMatch(key -> expected.values().get(key) != null && produced.values().get(key) != null);
        if (keysPresent) {
            boolean equal = keys.stream().allMatch(key -> {
                CollectionFieldType type = fieldType(collection, key);
                return normalize(type, expected.values().get(key)).equals(normalize(type, produced.values().get(key)));
            });
            return equal ? KEY_MATCH : -1;
        }
        double similarity = titleSimilarity(expected.title(), produced.title());
        if (lenientTitles && similarity < TITLE_SIMILARITY
                && (sharesReference(expected.title(), produced.title()) || (similarity >= LENIENT_OVERLAP && sameDate(collection, expected, produced)))) {
            return TITLE_SIMILARITY;
        }
        return similarity;
    }

    /** A token with a digit, at least three characters long and not a year, in both titles (an order or invoice number). */
    private static boolean sharesReference(String a, String b) {
        Set<String> references = new HashSet<>(Normalizers.tokens(a).stream().filter(EvalScorer::isReference).toList());
        return Normalizers.tokens(b).stream().filter(EvalScorer::isReference).anyMatch(references::contains);
    }

    private static boolean isReference(String token) {
        return token.length() >= 3 && token.chars().anyMatch(Character::isDigit) && !token.matches("20\\d\\d");
    }

    private static boolean sameDate(CollectionDto collection, EvalCase.ExpectedRecord expected, ProducedRecord produced) {
        return expected.values().entrySet().stream().anyMatch(entry -> {
            CollectionFieldType type = fieldType(collection, entry.getKey());
            return DATE_TYPES.contains(type) && entry.getValue() != null && produced.values().get(entry.getKey()) != null
                    && normalize(type, entry.getValue()).equals(normalize(type, produced.values().get(entry.getKey())));
        });
    }

    static double titleSimilarity(String a, String b) {
        Set<String> left = new HashSet<>(Normalizers.tokens(a));
        Set<String> right = new HashSet<>(Normalizers.tokens(b));
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> common = new HashSet<>(left);
        common.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) common.size() / union.size();
    }

    private void scoreFields(String slug, EvalCase.ExpectedRecord expected, ProducedRecord match, Map<String, int[]> perField, int[] dates) {
        CollectionDto collection = collections.get(slug);
        expected.values().forEach((field, value) -> {
            CollectionFieldType type = fieldType(collection, field);
            boolean isDate = DATE_COLLECTIONS.contains(slug) && DATE_TYPES.contains(type);
            boolean correct = match != null && match.values().get(field) != null
                    && normalize(type, value).equals(normalize(type, match.values().get(field)));
            if (match != null) {
                int[] counts = perField.computeIfAbsent(slug + "." + field, key -> new int[2]);
                counts[1]++;
                counts[0] += correct ? 1 : 0;
            }
            if (isDate) {
                dates[1]++;
                dates[0] += correct ? 1 : 0;
            }
        });
    }

    private int forbiddenHits(EvalCase evalCase, CaseResult result) {
        return (int) result.produced().stream()
                .filter(record -> evalCase.mustNotProduce().stream().anyMatch(forbidden -> forbidden.collection().equals(record.collection())
                        && (forbidden.title() == null || titleSimilarity(forbidden.title(), record.title()) >= TITLE_SIMILARITY)))
                .count();
    }

    private static CollectionFieldType fieldType(CollectionDto collection, String slug) {
        if (collection == null || collection.getFields() == null) {
            return CollectionFieldType.TEXT;
        }
        return collection.getFields().stream().filter(field -> slug.equals(field.getSlug())).findFirst()
                .map(CollectionFieldDto::getType).orElse(CollectionFieldType.TEXT);
    }

    private static String normalize(CollectionFieldType type, Object value) {
        return Optional.ofNullable(Normalizers.normalize(type, value)).orElse("");
    }

    private static EvalReport.FieldScore fieldScore(int[] counts) {
        return new EvalReport.FieldScore(counts[0], counts[1], ratio(counts[0], counts[1]));
    }

    private static Double ratio(int part, int whole) {
        return whole == 0 ? null : (double) part / whole;
    }

    private static EvalReport.Latency latency(List<Long> millis) {
        List<Long> sorted = millis.stream().sorted().toList();
        return new EvalReport.Latency(percentile(sorted, 0.50), percentile(sorted, 0.95));
    }

    /** Nearest-rank percentile; 0 for no samples. */
    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int rank = (int) Math.ceil(p * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }
}
