package com.example.contor.ai.eval;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compares what the collections hold after a mailbox replay with the expected end state. Scoring reuses
 * {@link EvalScorer} on one pseudo case (the end state is the expected record list, the collections are the produced
 * one), so precision/recall, field accuracy and relations mean the same as in the per-mail eval. On top it counts
 * <b>duplicates</b>: produced records that are the same entity as an expected record another produced record already
 * stands for (same match key — for people also the phone — or a similar title), e.g. the shared driver stored twice.
 */
final class EndStateComparison {

    record Result(EvalReport report, Map<String, Integer> duplicates) {
    }

    private EndStateComparison() {
    }

    static Result compare(BusinessCorpus.EndState expected, Map<String, List<ProducedRecord>> actual, List<ProducedRelation> actualRelations,
                          List<CollectionDto> collections) {
        Map<String, List<EvalCase.ExpectedRecord>> records = new LinkedHashMap<>();
        expected.records().forEach((slug, list) -> records.put(slug,
                list.stream().map(record -> new EvalCase.ExpectedRecord(record.title(), record.values())).toList()));
        List<EvalCase.ExpectedRelation> relations = expected.relations().stream()
                .map(relation -> new EvalCase.ExpectedRelation(relation.type(), relation.from(), relation.to())).toList();
        EvalCase pseudo = new EvalCase("end-state", null, new EvalCase.Expected(records, relations), List.of());
        List<ProducedRecord> produced = actual.values().stream().flatMap(List::stream).toList();
        EvalReport report = new EvalScorer(collections, Set.of("knows"))
                .score("replay", "end-state", List.of(pseudo), List.of(new CaseResult("end-state", produced, 0, null, actualRelations)));

        Map<String, CollectionDto> bySlug = new HashMap<>();
        collections.forEach(collection -> bySlug.put(collection.getSlug(), collection));
        Map<String, Integer> duplicates = new TreeMap<>();
        actual.forEach((slug, list) -> {
            Map<Integer, Integer> copies = new HashMap<>();
            for (ProducedRecord record : list) {
                List<EvalCase.ExpectedRecord> candidates = records.getOrDefault(slug, List.of());
                for (int i = 0; i < candidates.size(); i++) {
                    if (sameEntity(bySlug.get(slug), slug, candidates.get(i), record)) {
                        copies.merge(i, 1, Integer::sum);
                        break;
                    }
                }
            }
            int extra = copies.values().stream().mapToInt(n -> n - 1).sum();
            if (extra > 0) {
                duplicates.put(slug, extra);
            }
        });
        return new Result(report, duplicates);
    }

    private static boolean sameEntity(CollectionDto collection, String slug, EvalCase.ExpectedRecord expected, ProducedRecord produced) {
        List<String> keys = new ArrayList<>(collection != null && collection.getMatchKeys() != null ? collection.getMatchKeys() : List.of());
        if ("people".equals(slug)) {
            keys.add("phone");
        }
        for (String key : keys) {
            Object left = expected.values().get(key);
            Object right = produced.values().get(key);
            if (left != null && right != null) {
                CollectionFieldType type = "phone".equals(key) ? CollectionFieldType.PHONE : CollectionFieldType.TEXT;
                if (Normalizers.normalize(type, left).equals(Normalizers.normalize(type, right))) {
                    return true;
                }
            }
        }
        return EvalScorer.titleSimilarity(expected.title(), produced.title()) >= EvalScorer.TITLE_SIMILARITY;
    }
}
