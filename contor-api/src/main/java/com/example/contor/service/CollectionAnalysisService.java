package com.example.contor.service;

import com.example.contor.ai.CollectionPlan;
import com.example.contor.ai.EvidencePack.AnalysisInput;
import com.example.contor.ai.EvidencePack.AnalysisRow;
import com.example.contor.ai.EvidencePack.RowSource;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.ReportScope.CollectionScope;
import com.example.contor.fieldtype.ColumnSpec;
import com.example.contor.fieldtype.FieldTypeRegistry;
import com.example.contor.fieldtype.RelationFieldTypeHandler;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationTypeRepository;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Aggregate;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Bucket;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.GroupBy;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Measure;
import com.example.contor.persistence.dynamic.DynamicRecordRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.name;

/**
 * Figures for a report over a collection (C4): a catalog of the collections for the model to plan with, the analyses
 * of its {@link CollectionPlan} computed over all matching records ({@link DynamicAggregateRepository}), and an overview
 * of the anchor collection. Everything the model planned is checked here: unknown collections, fields that cannot be
 * grouped, unknown relation types and invalid filters are dropped; a measure of a field that is no number becomes a
 * count. Rankings, ties and top-N are decided here, never by the model: by the measure in the currency most records
 * use (amounts are not converted), then by count, then by label. Records without a group value are not ranked.
 */
@RequiredArgsConstructor
@Service
public class CollectionAnalysisService {

    /** Records a figure row cites at most. */
    static final int MAX_ROW_SOURCES = 20;
    private static final int MAX_OVERVIEW_VALUES = 12;
    private static final String EACH = "each";
    private static final String LINK = "link:";
    private static final Set<FieldType> TEXT_GROUPS = EnumSet.of(FieldType.TEXT, FieldType.ENUM, FieldType.STATUS, FieldType.EMAIL,
            FieldType.URL, FieldType.PHONE);
    private static final Set<FieldType> NUMBERS = EnumSet.of(FieldType.NUMBER, FieldType.MONEY);
    private static final Set<FieldType> CHOICES = EnumSet.of(FieldType.ENUM, FieldType.STATUS);

    private final CollectionRepository collectionRepository;
    private final RelationTypeRepository relationTypeRepository;
    private final RecordService recordService;
    private final DynamicAggregateRepository aggregateRepository;
    private final DynamicRecordRepository dynamicRecordRepository;
    private final FieldTypeRegistry fieldTypeRegistry;

    /** How the records of one analysis are grouped, and what the group column is called. */
    private record Grouping(GroupBy groupBy, String name, Long labelCollectionId) {}

    /** The measured field; {@code null} field = count. */
    private record Measured(Measure measure, CollectionField field) {

        String header() {
            return switch (measure) {
                case SUM -> "Sum of " + field.getName();
                case AVG -> "Average of " + field.getName();
                case MIN -> "Minimum of " + field.getName();
                case MAX -> "Maximum of " + field.getName();
                case COUNT -> "Count";
            };
        }

        /** The header in a sentence: "sum of Total". */
        String phrase() {
            String header = header();
            return Character.toLowerCase(header.charAt(0)) + header.substring(1);
        }
    }

    /** One group with its values per currency ("" without a currency). */
    private static final class Group {

        private final Object key;
        private String label;
        private long count;
        private long withValue;
        private final Map<String, BigDecimal> values = new LinkedHashMap<>();
        private final TreeSet<Long> recordIds = new TreeSet<>();

        Group(Object key) {
            this.key = key;
        }

        BigDecimal value(String currency) {
            return values.get(currency);
        }
    }

    /** The slugs of the collections the model may analyse. */
    @Transactional
    public List<String> collectionSlugs() {
        return collections().stream().map(collection -> collection.getSlug().value()).toList();
    }

    /** The collections with their record counts and fields, and the relation types, as the plan prompt shows them. */
    @Transactional
    public String catalog(Long anchorCollectionId) {
        StringBuilder text = new StringBuilder("Collections (fields as slug (type, name)):\n");
        for (Collection collection : collections()) {
            long records = dynamicRecordRepository.count(collection.getTableName(), DSL.noCondition());
            text.append("- ").append(collection.getSlug().value()).append(": ").append(collection.getName()).append(", ")
                    .append(records).append(records == 1 ? " record" : " records")
                    .append(collection.getId().id().equals(anchorCollectionId) ? " (the collection the user is looking at)" : "");
            List<String> fields = fields(collection).stream().map(this::describe).toList();
            if (!fields.isEmpty()) {
                text.append(" — fields: ").append(String.join(", ", fields));
            }
            text.append('\n');
        }
        List<RelationType> types = relationTypeRepository.findAllByOrderByNameAsc().stream()
                .filter(type -> type.getFromCollection() != null && type.getToCollection() != null)
                .toList();
        if (!types.isEmpty()) {
            text.append("Relation types (groupBy \"link:<slug>\" groups records by the record at the other end):\n");
            types.forEach(type -> text.append("- ").append(type.getSlug().value()).append(": ").append(type.getFromCollection().getName())
                    .append(' ').append(type.getName()).append(' ').append(type.getToCollection().getName()).append('\n'));
        }
        return text.toString();
    }

    /**
     * The analyses of {@code plan} that make sense, computed. The table's {@code scope} (filters, search) applies to the
     * analyses of the anchor collection.
     */
    @Transactional
    public List<AnalysisInput> run(CollectionPlan plan, Long anchorCollectionId, CollectionScope scope) {
        Map<String, Collection> bySlug = new LinkedHashMap<>();
        collections().forEach(collection -> bySlug.put(collection.getSlug().value(), collection));
        List<AnalysisInput> analyses = new ArrayList<>();
        for (CollectionPlan.Analysis analysis : plan.analyses()) {
            Collection collection = bySlug.get(analysis.collection());
            if (collection == null) {
                continue;
            }
            CollectionScope applied = collection.getId().id().equals(anchorCollectionId) && scope != null ? scope : new CollectionScope(null, null);
            List<String> filters = new ArrayList<>(validFilters(collection, analysis.filters()));
            filters.addAll(applied.filters());
            Measured measured = measured(collection, analysis.measure());
            if (EACH.equals(analysis.groupBy())) {
                analyses.add(eachRecord(collection, analysis, measured, filters, applied.search()));
                continue;
            }
            grouping(collection, analysis.groupBy()).ifPresent(grouping ->
                    analyses.add(grouped(collection, analysis, grouping, measured, filters, applied.search())));
        }
        return analyses;
    }

    /**
     * The matching records of a collection in a few lines: how many, the values of its choice fields, the sums of its
     * number and money fields.
     */
    @Transactional
    public String overview(Long collectionId, CollectionScope scope) {
        Collection collection = collections().stream().filter(candidate -> candidate.getId().id().equals(collectionId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Collection " + collectionId + " is gone"));
        CollectionScope applied = scope != null ? scope : new CollectionScope(null, null);
        Condition condition = recordService.condition(collectionId, applied.filters(), applied.search());
        long total = dynamicRecordRepository.count(collection.getTableName(), condition);
        StringBuilder text = new StringBuilder("Overview of ").append(collection.getName()).append(":\n")
                .append(collection.getName()).append(": ").append(total).append(total == 1 ? " record" : " records").append(scopeText(applied))
                .append('\n');
        for (CollectionField field : fields(collection)) {
            List<ColumnSpec> columns = columns(field);
            if (CHOICES.contains(field.getType())) {
                List<Bucket> buckets = aggregateRepository.aggregate(collection.getTableName(), condition,
                        new Aggregate(new GroupBy.ByColumn(typed(columns.getFirst()), true), Measure.COUNT, null, null));
                List<String> values = buckets.stream()
                        .sorted(Comparator.comparingLong(Bucket::count).reversed().thenComparing(bucket -> String.valueOf(bucket.label())))
                        .map(bucket -> (bucket.key() == null ? "no value" : bucket.label()) + " " + bucket.count())
                        .toList();
                if (!values.isEmpty()) {
                    text.append(field.getName()).append(": ").append(String.join(", ", values.stream().limit(MAX_OVERVIEW_VALUES).toList()))
                            .append(values.size() > MAX_OVERVIEW_VALUES ? " and " + (values.size() - MAX_OVERVIEW_VALUES) + " more" : "")
                            .append('\n');
                }
            } else if (NUMBERS.contains(field.getType())) {
                List<Bucket> buckets = aggregateRepository.aggregate(collection.getTableName(), condition,
                        new Aggregate(new GroupBy.None(), Measure.SUM, number(columns.getFirst()),
                                columns.size() > 1 ? text(columns.get(1)) : null));
                long withValue = buckets.stream().mapToLong(Bucket::withValue).sum();
                List<String> sums = buckets.stream().filter(bucket -> bucket.value() != null)
                        .sorted(Comparator.comparingLong(Bucket::withValue).reversed())
                        .map(bucket -> amount(bucket.value(), bucket.currency()) + " (" + records(bucket.withValue()) + ")")
                        .toList();
                text.append(field.getName()).append(": ").append(sums.isEmpty() ? "no values" : "sum " + String.join(", ", sums));
                if (!sums.isEmpty() && total > withValue) {
                    text.append(", ").append(records(total - withValue)).append(" without a value");
                }
                text.append('\n');
            }
        }
        return text.toString();
    }

    private AnalysisInput grouped(Collection collection, CollectionPlan.Analysis analysis, Grouping grouping, Measured measured,
                                  List<String> filters, String search) {
        Condition condition = recordService.condition(collection.getId().id(), filters, search);
        long total = dynamicRecordRepository.count(collection.getTableName(), condition);
        List<ColumnSpec> valueColumns = measured.field() != null ? columns(measured.field()) : List.of();
        Aggregate aggregate = new Aggregate(grouping.groupBy(), measured.measure(),
                valueColumns.isEmpty() ? null : number(valueColumns.getFirst()), valueColumns.size() > 1 ? text(valueColumns.get(1)) : null);
        Map<Object, Group> groups = new LinkedHashMap<>();
        for (Bucket bucket : aggregateRepository.aggregate(collection.getTableName(), condition, aggregate)) {
            Group group = groups.computeIfAbsent(bucket.key(), Group::new);
            group.label = group.label != null ? group.label : bucket.label();
            group.count += bucket.count();
            group.withValue += bucket.withValue();
            if (bucket.value() != null) {
                group.values.put(Optional.ofNullable(bucket.currency()).orElse(""), bucket.value());
            }
            group.recordIds.addAll(bucket.recordIds());
        }
        boolean all = grouping.groupBy() instanceof GroupBy.None;
        Group none = all ? null : groups.remove(null);
        String main = mainCurrency(List.copyOf(groups.values()));
        List<Group> ranked = new ArrayList<>(groups.values());
        ranked.sort(ranking(measured, main, analysis.ascending()));
        List<Group> shown = ranked.stream().limit(analysis.limit()).toList();
        labels(shown, grouping);

        Map<Long, RecordDto> records = new HashMap<>();
        List<Long> cited = shown.stream().flatMap(group -> group.recordIds.stream().limit(MAX_ROW_SOURCES)).distinct().toList();
        recordService.findByIds(collection.getId().id(), cited).forEach(record -> records.put(record.getId(), record));

        List<String> columns = new ArrayList<>(List.of("#", grouping.name(), collection.getName()));
        if (measured.field() != null) {
            columns.add(measured.header());
        }
        List<AnalysisRow> rows = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            Group group = shown.get(i);
            List<String> cells = new ArrayList<>(List.of(String.valueOf(i + 1), all ? "All matching records" : group.label,
                    String.valueOf(group.count)));
            if (measured.field() != null) {
                cells.add(values(group, main, measured.measure()));
            }
            rows.add(new AnalysisRow(cells, sources(collection, group.recordIds.stream().limit(MAX_ROW_SOURCES).toList(), records,
                    measured.field())));
        }
        String title = !analysis.title().isBlank() ? analysis.title() : collection.getName() + (all ? "" : " by " + grouping.name());
        String description = total + " " + collection.getName() + filterText(filters, search)
                + (all ? ", all together" : ", grouped by " + grouping.name())
                + (measured.field() != null ? ", " + measured.phrase() : ", counted")
                + (all ? "" : ", " + order(measured, analysis.ascending()) + ", top " + shown.size() + " of " + ranked.size()
                + (ranked.size() == 1 ? " group" : " groups"));
        List<String> notes = new ArrayList<>();
        if (none != null) {
            notes.add(none.count + " of the records " + (none.count == 1 ? "has" : "have") + " no " + grouping.name() + "; they are not ranked.");
        }
        long withValue = groups.values().stream().mapToLong(group -> group.withValue).sum() + (none != null ? none.withValue : 0);
        if (measured.field() != null && total > withValue) {
            notes.add((total - withValue) + " of the " + total + " records have no " + measured.field().getName() + ".");
        }
        if (groups.values().stream().flatMap(group -> group.values.keySet().stream()).distinct().count() > 1) {
            notes.add("Amounts in different currencies are not converted; groups are ranked by their " + main + " amount.");
        }
        if (shown.stream().anyMatch(group -> group.recordIds.size() > MAX_ROW_SOURCES)) {
            notes.add("A row cites at most " + MAX_ROW_SOURCES + " of its records.");
        }
        return new AnalysisInput(title, description, columns, columns.size() - 1, rows, notes.isEmpty() ? null : String.join(" ", notes));
    }

    /** Every matching record on its own row, ranked by the measured field (records without a value are left out). */
    private AnalysisInput eachRecord(Collection collection, CollectionPlan.Analysis analysis, Measured measured, List<String> filters,
                                     String search) {
        CollectionField field = measured.field();
        List<String> applied = new ArrayList<>(filters);
        String sort = null;
        if (field != null) {
            applied.add(field.getSlug().value() + ":notempty");
            sort = (analysis.ascending() ? "" : "-") + field.getSlug().value();
        }
        RecordPageDto page = recordService.listRecords(collection.getId().id(), applied, search, sort, 0, analysis.limit());
        List<String> columns = new ArrayList<>(List.of("#", collection.getName()));
        if (field != null) {
            columns.add(field.getName());
        }
        Map<Long, RecordDto> records = new HashMap<>();
        page.getItems().forEach(record -> records.put(record.getId(), record));
        List<AnalysisRow> rows = new ArrayList<>();
        for (int i = 0; i < page.getItems().size(); i++) {
            RecordDto record = page.getItems().get(i);
            List<String> cells = new ArrayList<>(List.of(String.valueOf(i + 1), title(collection, record)));
            if (field != null) {
                cells.add(format(record.getValues().get(field.getSlug().value())));
            }
            rows.add(new AnalysisRow(cells, sources(collection, List.of(record.getId()), records, field)));
        }
        String title = !analysis.title().isBlank() ? analysis.title() : collection.getName();
        String description = page.getTotalElements() + " " + collection.getName() + filterText(filters, search)
                + (field != null ? " with a " + field.getName() + ", each record on its own row, sorted by " + field.getName() + " ("
                + (analysis.ascending() ? "smallest" : "largest") + " first)" : ", newest first")
                + ", top " + rows.size() + " of " + page.getTotalElements();
        String note = field != null && field.getType() == FieldType.MONEY ? "Amounts in different currencies are not converted." : null;
        return new AnalysisInput(title, description, columns, columns.size() - 1, rows, note);
    }

    private List<RowSource> sources(Collection collection, List<Long> ids, Map<Long, RecordDto> records, CollectionField field) {
        List<RowSource> sources = new ArrayList<>();
        for (Long id : ids) {
            RecordDto record = records.get(id);
            if (record == null) {
                continue;
            }
            Object value = field != null ? record.getValues().get(field.getSlug().value()) : null;
            sources.add(value != null
                    ? new RowSource(collection.getId().id(), collection.getName(), id, title(collection, record), field.getSlug().value(),
                    field.getName(), value)
                    : new RowSource(collection.getId().id(), collection.getName(), id, title(collection, record), null, null, null));
        }
        return sources;
    }

    /** Titles of the records a reference field or relation type groups by. */
    private void labels(List<Group> groups, Grouping grouping) {
        if (grouping.labelCollectionId() == null) {
            return;
        }
        List<Long> keys = groups.stream().map(group -> group.key).filter(Long.class::isInstance).map(Long.class::cast).toList();
        Map<Long, String> titles = new HashMap<>();
        if (!keys.isEmpty()) {
            recordService.findByIds(grouping.labelCollectionId(), keys).forEach(record -> titles.put(record.getId(), record.getTitle()));
        }
        groups.forEach(group -> group.label = Optional.ofNullable(titles.get(group.key)).filter(title -> !title.isBlank())
                .orElse("#" + group.key));
    }

    private Optional<Grouping> grouping(Collection collection, String groupBy) {
        if (groupBy.isEmpty()) {
            return Optional.of(new Grouping(new GroupBy.None(), "All", null));
        }
        if (groupBy.startsWith(LINK)) {
            String slug = groupBy.substring(LINK.length()).strip();
            Long own = collection.getId().id();
            return relationTypeRepository.findAllByOrderByNameAsc().stream()
                    .filter(type -> type.getSlug().value().equals(slug) && type.getFromCollection() != null && type.getToCollection() != null)
                    .filter(type -> type.getFromCollection().getId().id().equals(own) || type.getToCollection().getId().id().equals(own))
                    .findFirst()
                    .map(type -> {
                        boolean outgoing = type.getFromCollection().getId().id().equals(own);
                        Collection other = outgoing ? type.getToCollection() : type.getFromCollection();
                        return new Grouping(new GroupBy.ByLink(type.getId().id(), own, other.getId().id(), outgoing), other.getName(),
                                other.getId().id());
                    });
        }
        return field(collection, groupBy).flatMap(field -> {
            Field<?> column = typed(columns(field).getFirst());
            if (TEXT_GROUPS.contains(field.getType())) {
                return Optional.of(new Grouping(new GroupBy.ByColumn(column, true), field.getName(), null));
            }
            if (field.getType() == FieldType.BOOLEAN) {
                return Optional.of(new Grouping(new GroupBy.ByColumn(column, false), field.getName(), null));
            }
            if (field.getType() == FieldType.RELATION) {
                return Optional.of(new Grouping(new GroupBy.ByColumn(column, false), field.getName(),
                        RelationFieldTypeHandler.targetCollectionId(field)));
            }
            return Optional.empty();
        });
    }

    /** {@code sum:total} → the sum of a NUMBER or MONEY field; anything else is a count. */
    private Measured measured(Collection collection, String measure) {
        String[] parts = measure.split(":", 2);
        if (parts.length == 2) {
            Measure op = switch (parts[0].strip()) {
                case "sum" -> Measure.SUM;
                case "avg", "average" -> Measure.AVG;
                case "min" -> Measure.MIN;
                case "max" -> Measure.MAX;
                default -> null;
            };
            Optional<CollectionField> field = field(collection, parts[1].strip()).filter(candidate -> NUMBERS.contains(candidate.getType()));
            if (op != null && field.isPresent()) {
                return new Measured(op, field.get());
            }
        }
        return new Measured(Measure.COUNT, null);
    }

    /** The filters the collection understands; each is checked on its own, so one bad filter does not drop the others. */
    private List<String> validFilters(Collection collection, List<String> filters) {
        List<String> valid = new ArrayList<>();
        for (String filter : filters) {
            try {
                recordService.condition(collection.getId().id(), List.of(filter), null);
                valid.add(filter);
            } catch (IllegalArgumentException e) {
                // invented by the model: left out
            }
        }
        return valid;
    }

    private static Comparator<Group> ranking(Measured measured, String main, boolean ascending) {
        Comparator<Group> byLabel = Comparator.comparing(group -> String.valueOf(group.label).toLowerCase(Locale.ROOT));
        Comparator<Group> byCount = Comparator.comparingLong(group -> group.count);
        if (measured.field() == null) {
            return (ascending ? byCount : byCount.reversed()).thenComparing(byLabel);
        }
        Comparator<BigDecimal> values = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing((Group group) -> group.value(main), Comparator.nullsLast(values))
                .thenComparing(byCount.reversed())
                .thenComparing(byLabel);
    }

    /** The currency (or "" for plain numbers) most records with a value use; ties go to the alphabetically first. */
    private static String mainCurrency(List<Group> groups) {
        Map<String, Long> records = new HashMap<>();
        groups.forEach(group -> group.values.keySet().forEach(currency -> records.merge(currency, group.withValue, Long::sum)));
        return records.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .findFirst().orElse("");
    }

    private static String values(Group group, String main, Measure measure) {
        if (group.values.isEmpty()) {
            return "–";
        }
        List<String> currencies = new ArrayList<>(group.values.keySet());
        currencies.sort(Comparator.comparing((String currency) -> !currency.equals(main)).thenComparing(Comparator.naturalOrder()));
        return currencies.stream().map(currency -> amount(round(group.value(currency), measure), currency)).collect(Collectors.joining(", "));
    }

    private static BigDecimal round(BigDecimal value, Measure measure) {
        return measure == Measure.AVG ? value.setScale(2, RoundingMode.HALF_UP) : value;
    }

    private static String amount(BigDecimal value, String currency) {
        String number = value.stripTrailingZeros().toPlainString();
        return currency == null || currency.isBlank() ? number : number + " " + currency.strip();
    }

    /** A record value as a table cell: money as "110 EUR", numbers without trailing zeros. */
    private static String format(Object value) {
        if (value instanceof Map<?, ?> money && money.containsKey("amount")) {
            return amount(new BigDecimal(String.valueOf(money.get("amount"))), money.get("currency") == null ? null : money.get("currency").toString());
        }
        if (value instanceof BigDecimal number) {
            return number.stripTrailingZeros().toPlainString();
        }
        return value == null ? "–" : value.toString();
    }

    private static String order(Measured measured, boolean ascending) {
        if (measured.field() == null) {
            return ascending ? "fewest first" : "most first";
        }
        return ascending ? "smallest first" : "largest first";
    }

    private static String records(long count) {
        return count + (count == 1 ? " record" : " records");
    }

    private static String scopeText(CollectionScope scope) {
        List<String> parts = new ArrayList<>();
        if (!scope.filters().isEmpty()) {
            parts.add("filters: " + String.join(", ", scope.filters()));
        }
        if (scope.search() != null) {
            parts.add("search \"" + scope.search() + "\"");
        }
        return parts.isEmpty() ? "" : " (" + String.join("; ", parts) + ")";
    }

    private static String filterText(List<String> filters, String search) {
        String scope = scopeText(new CollectionScope(filters, search));
        return scope.isEmpty() ? "" : " matching" + scope;
    }

    private static String title(Collection collection, RecordDto record) {
        return record.getTitle() != null && !record.getTitle().isBlank() ? record.getTitle() : collection.getName() + " #" + record.getId();
    }

    private String describe(CollectionField field) {
        String type = field.getType().name().toLowerCase(Locale.ROOT);
        if (CHOICES.contains(field.getType())) {
            Object values = field.getOptions().get(field.getType() == FieldType.STATUS ? "states" : "values");
            if (values instanceof List<?> list && !list.isEmpty()) {
                return field.getSlug().value() + " (" + type + ": " + list.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")";
            }
        }
        if (field.getType() == FieldType.RELATION) {
            Long target = RelationFieldTypeHandler.targetCollectionId(field);
            String slug = collections().stream().filter(collection -> collection.getId().id().equals(target)).findFirst()
                    .map(collection -> collection.getSlug().value()).orElse("another collection");
            return field.getSlug().value() + " (reference to " + slug + ", " + field.getName() + ")";
        }
        return field.getSlug().value() + " (" + type + ", " + field.getName() + ")";
    }

    private List<Collection> collections() {
        return collectionRepository.findByArchivedAtIsNullOrderByNameAsc();
    }

    private static List<CollectionField> fields(Collection collection) {
        return collection.getFields().stream()
                .filter(field -> field.getArchivedAt() == null)
                .sorted(Comparator.comparingInt(CollectionField::getPosition))
                .toList();
    }

    private static Optional<CollectionField> field(Collection collection, String slug) {
        return fields(collection).stream().filter(field -> field.getSlug().value().equals(slug)).findFirst();
    }

    private List<ColumnSpec> columns(CollectionField field) {
        return fieldTypeRegistry.handler(field.getType()).columns(field);
    }

    private static Field<?> typed(ColumnSpec column) {
        return DSL.field(name(column.name()), column.dataType());
    }

    private static Field<BigDecimal> number(ColumnSpec column) {
        return DSL.field(name(column.name()), SQLDataType.NUMERIC);
    }

    private static Field<String> text(ColumnSpec column) {
        return DSL.field(name(column.name()), SQLDataType.CLOB);
    }
}
