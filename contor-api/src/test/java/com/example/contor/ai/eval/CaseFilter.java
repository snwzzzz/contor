package com.example.contor.ai.eval;

import org.junit.jupiter.api.Assumptions;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Which eval cases to run: {@code -Dai.eval.cases=invoice-*,trip-de-train} runs only the cases whose id matches one of
 * the comma-separated globs ({@code *}, {@code ?}; case-insensitive). Without it, every case runs.
 */
final class CaseFilter {

    private final String spec;
    private final List<Pattern> patterns;

    private CaseFilter(String spec, List<Pattern> patterns) {
        this.spec = spec;
        this.patterns = patterns;
    }

    static CaseFilter of(String spec) {
        if (spec == null || spec.isBlank()) {
            return new CaseFilter("", List.of());
        }
        return new CaseFilter(spec.trim(), Arrays.stream(spec.split(",")).map(String::trim).filter(glob -> !glob.isEmpty())
                .map(CaseFilter::compile).toList());
    }

    static CaseFilter fromSystemProperty() {
        return of(System.getProperty("ai.eval.cases"));
    }

    boolean isAll() {
        return patterns.isEmpty();
    }

    String spec() {
        return spec;
    }

    <T> List<T> select(List<T> cases, Function<T, String> id) {
        if (isAll()) {
            return cases;
        }
        return cases.stream().filter(evalCase -> patterns.stream().anyMatch(pattern -> pattern.matcher(id.apply(evalCase)).matches())).toList();
    }

    /** {@link #select}, skipping the calling test when the filter leaves no case of its corpus. */
    <T> List<T> selectOrSkip(List<T> cases, Function<T, String> id) {
        List<T> selected = select(cases, id);
        Assumptions.assumeFalse(selected.isEmpty(), () -> "no case of this eval matches ai.eval.cases=" + spec);
        return selected;
    }

    private static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
