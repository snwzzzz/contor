package com.example.contor.ai;

import com.example.contor.ai.EvidencePack.Source;
import com.example.contor.domain.CitationType;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The deterministic fact check of one report sentence against an {@link EvidencePack}, before any model is asked:
 * <ul>
 *     <li>it cites at least one existing source (unknown and repeated tokens are dropped);</li>
 *     <li>a quote is kept only when it is verbatim (see {@link EmailParagraphs#normalize}) in the email paragraph it
 *     names; a kept quote's paragraph counts as a cited source;</li>
 *     <li>every date in the sentence (ISO, {@code 6.9.2026}, {@code September 6, 2026}, {@code 6. September 2026},
 *     English and German month names) occurs in a cited source;</li>
 *     <li>every other number with more than one digit occurs in a cited source in any common notation
 *     ({@code 1.250,00} = {@code 1,250} = {@code 1250}), or is the number of days between two cited dates. Single
 *     digits are not checked (counts and ordinals the sentence may compute).</li>
 * </ul>
 */
public final class ClaimChecks {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,']\\d+)*");
    private static final Pattern INLINE_TOKEN = Pattern.compile("\\[[A-Za-z]\\d+(?:\\.\\d+)?]");
    private static final Map<String, Integer> MONTHS = months();
    private static final String MONTH = MONTHS.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote).collect(Collectors.joining("|"));
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern DOTTED_DATE = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\b");
    private static final Pattern MONTH_FIRST = Pattern.compile("(?iu)\\b(" + MONTH + ")\\.? (\\d{1,2})(?:st|nd|rd|th)?,? (\\d{4})\\b");
    private static final Pattern DAY_FIRST = Pattern.compile("(?iu)\\b(\\d{1,2})(?:\\.|st|nd|rd|th)? (" + MONTH + ")\\.?,? (\\d{4})\\b");

    private ClaimChecks() {
    }

    /** A verbatim quote from the email paragraph {@code token}. */
    public record Quote(String token, String text) {}

    /**
     * @param refs     the valid cited tokens, normalized, without repeats (quoted paragraphs included)
     * @param quotes   the quotes that are verbatim in their paragraph
     * @param problems why the sentence fails; empty when it passes
     */
    public record Result(List<String> refs, List<Quote> quotes, List<String> problems) {

        public boolean passed() {
            return problems.isEmpty();
        }
    }

    public static Result check(String text, List<String> refs, List<Quote> quotes, EvidencePack pack) {
        Set<String> valid = new LinkedHashSet<>();
        for (String ref : refs) {
            String token = EvidencePack.normalizeToken(ref);
            if (token != null && pack.source(token).isPresent()) {
                valid.add(token);
            }
        }
        List<Quote> keptQuotes = new ArrayList<>();
        for (Quote quote : quotes) {
            Source source = pack.source(quote.token()).orElse(null);
            if (source != null && source.type() == CitationType.EMAIL_PARAGRAPH && quote.text() != null && !quote.text().isBlank()
                    && EmailParagraphs.normalize(source.text()).contains(EmailParagraphs.normalize(quote.text()))) {
                keptQuotes.add(new Quote(source.token(), quote.text().strip()));
                valid.add(source.token());
            }
        }
        List<String> problems = new ArrayList<>();
        if (text == null || text.isBlank()) {
            problems.add("is empty");
            return new Result(List.copyOf(valid), keptQuotes, problems);
        }
        if (valid.isEmpty()) {
            problems.add("cites no source");
            return new Result(List.of(), keptQuotes, problems);
        }
        List<Source> cited = valid.stream().map(token -> pack.source(token).orElseThrow()).toList();
        String citedText = cited.stream().map(source -> source.text() + " " + source.label()).collect(Collectors.joining(" \n "));
        Set<LocalDate> citedDates = new HashSet<>();
        dates(citedText).forEach(found -> citedDates.add(found.date()));
        cited.stream().map(Source::date).filter(date -> date != null).forEach(citedDates::add);
        Set<String> citedNumbers = new HashSet<>(sourceNumbers(citedText));
        for (LocalDate a : citedDates) {
            for (LocalDate b : citedDates) {
                citedNumbers.add(String.valueOf(Math.abs(ChronoUnit.DAYS.between(a, b))));
            }
        }

        String claim = INLINE_TOKEN.matcher(text).replaceAll(" ");
        StringBuilder withoutDates = new StringBuilder(claim);
        List<FoundDate> claimDates = dates(claim);
        claimDates.stream().sorted(Comparator.comparingInt(FoundDate::start).reversed())
                .forEach(found -> withoutDates.replace(found.start(), found.end(), " "));
        for (FoundDate found : claimDates) {
            if (!citedDates.contains(found.date())) {
                problems.add(found.raw() + " is not in its sources");
            }
        }
        Matcher numbers = NUMBER.matcher(withoutDates);
        while (numbers.find()) {
            String token = numbers.group();
            if (token.length() == 1) {
                continue;
            }
            if (candidates(token).stream().noneMatch(citedNumbers::contains)) {
                problems.add(token + " is not in its sources");
            }
        }
        return new Result(List.copyOf(valid), keptQuotes, problems);
    }

    private record FoundDate(LocalDate date, int start, int end, String raw) {}

    /** Dates written in {@code text}, without overlaps (the first pattern that matches a span wins). */
    private static List<FoundDate> dates(String text) {
        List<FoundDate> found = new ArrayList<>();
        collect(found, ISO_DATE.matcher(text), m -> date(m.group(1), m.group(2), m.group(3)));
        collect(found, DOTTED_DATE.matcher(text), m -> date(m.group(3), m.group(2), m.group(1)));
        collect(found, MONTH_FIRST.matcher(text), m -> date(m.group(3), month(m.group(1)), m.group(2)));
        collect(found, DAY_FIRST.matcher(text), m -> date(m.group(3), month(m.group(2)), m.group(1)));
        return found;
    }

    private static void collect(List<FoundDate> found, Matcher matcher, java.util.function.Function<Matcher, LocalDate> parse) {
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (found.stream().anyMatch(other -> start < other.end() && other.start() < end)) {
                continue;
            }
            LocalDate date = parse.apply(matcher);
            if (date != null) {
                found.add(new FoundDate(date, start, end, matcher.group()));
            }
        }
    }

    private static LocalDate date(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static String month(String name) {
        return String.valueOf(MONTHS.getOrDefault(name.toLowerCase(Locale.ROOT).replace(".", ""), 0));
    }

    /** Every reading of the numbers in a source, plus each separated group on its own (dates, ids). */
    private static Set<String> sourceNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            numbers.addAll(candidates(token));
            for (String group : token.split("[.,']")) {
                numbers.addAll(candidates(group));
            }
        }
        return numbers;
    }

    /** {@code 1.250,00} → 1250 and 125000; {@code 1,25} → 1.25 and 125; plain digits → themselves. */
    private static Set<String> candidates(String token) {
        Set<String> values = new HashSet<>();
        String digits = token.replaceAll("[.,']", "");
        values.add(canonical(digits));
        int last = Math.max(token.lastIndexOf('.'), token.lastIndexOf(','));
        if (last > 0) {
            values.add(canonical(token.substring(0, last).replaceAll("[.,']", "") + "." + token.substring(last + 1)));
        }
        return values;
    }

    private static String canonical(String number) {
        BigDecimal value = new BigDecimal(number).stripTrailingZeros();
        return value.signum() == 0 ? "0" : value.toPlainString();
    }

    private static Map<String, Integer> months() {
        Map<String, Integer> months = new LinkedHashMap<>();
        String[][] names = {
                {"january", "januar", "jan", "jänner"}, {"february", "februar", "feb"}, {"march", "märz", "maerz", "mar", "mär"},
                {"april", "apr"}, {"may", "mai"}, {"june", "juni", "jun"}, {"july", "juli", "jul"}, {"august", "aug"},
                {"september", "sept", "sep"}, {"october", "oktober", "oct", "okt"}, {"november", "nov"},
                {"december", "dezember", "dec", "dez"}};
        for (int i = 0; i < names.length; i++) {
            for (String name : names[i]) {
                months.put(name, i + 1);
            }
        }
        return months;
    }
}
