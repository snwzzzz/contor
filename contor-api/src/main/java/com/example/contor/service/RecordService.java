package com.example.contor.service;

import com.example.contor.api.model.NewRecordCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.api.model.UpdateRecordCommand;
import com.example.contor.domain.ActorType;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.FieldType;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.exception.RecordValidationException;
import com.example.contor.fieldtype.ColumnSpec;
import com.example.contor.fieldtype.FieldTypeHandler;
import com.example.contor.fieldtype.FieldTypeRegistry;
import com.example.contor.fieldtype.RelationFieldTypeHandler;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.dynamic.DynamicRecordRepository;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Generic record CRUD on generated collection tables, driven by the collection's field metadata and the field type
 * handlers. Values are keyed by field slug; every violated rule is reported at once. Archived fields are invisible
 * and read-only. Records are always addressed as (collection id, record id).
 */
@RequiredArgsConstructor
@Service
public class RecordService {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final CollectionRepository collectionRepository;
    private final DynamicRecordRepository dynamicRecordRepository;
    private final FieldTypeRegistry fieldTypeRegistry;
    private final ObjectMapper objectMapper;

    /** The collection with its active fields in position order. */
    private record Schema(Collection collection, List<CollectionField> fields) {

        String table() {
            return collection.getTableName();
        }

        Long id() {
            return collection.getId().id();
        }

        CollectionField field(String slug) {
            return fields.stream().filter(field -> field.getSlug().value().equals(slug)).findFirst().orElse(null);
        }
    }

    /** Column to filter/sort on, with a parser for query-string values. */
    private record Target(Field<Object> column, Function<String, Object> parser, boolean text) {
    }

    @Transactional
    /** {@code query}: global search, case-insensitive, in the title and every text-like field; AND-combined with the filters. */
    public RecordPageDto listRecords(Long collectionId, List<String> filters, String query, String sort, Integer page, Integer size) {
        Schema schema = schema(collectionId);
        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        Condition condition = DSL.noCondition();
        for (String filter : filters != null ? filters : List.<String>of()) {
            condition = condition.and(parseFilter(schema, filter));
        }
        if (query != null && !query.isBlank()) {
            condition = condition.and(searchCondition(schema, query.strip()));
        }
        SortField<?> sortField = parseSort(schema, sort);

        List<RecordDto> items = dynamicRecordRepository
                .find(schema.table(), columns(schema), condition, sortField, pageNumber * pageSize, pageSize).stream()
                .map(record -> toDto(schema, record))
                .toList();
        long total = dynamicRecordRepository.count(schema.table(), condition);
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        return new RecordPageDto(new ArrayList<>(items), pageNumber, pageSize, total, totalPages);
    }

    @Transactional
    public RecordDto getRecord(Long collectionId, Long recordId) {
        Schema schema = schema(collectionId);
        return toDto(schema, load(schema, recordId));
    }

    @Transactional
    public RecordDto createRecord(Long collectionId, NewRecordCommand command) {
        return createRecord(collectionId, command, CurrentActor.type());
    }

    /** {@code createdBy} is stored in the record's {@code created_by} column (e.g. AI for applied proposals). */
    @Transactional
    public RecordDto createRecord(Long collectionId, NewRecordCommand command, ActorType createdBy) {
        Schema schema = schema(collectionId);
        Map<String, Object> input = command.getValues() != null ? command.getValues() : Map.of();
        List<String> violations = new ArrayList<>(unknownKeys(schema, input));

        Map<CollectionField, Object> values = new LinkedHashMap<>();
        for (CollectionField field : schema.fields()) {
            FieldTypeHandler handler = handler(field);
            String slug = field.getSlug().value();
            Object value = input.containsKey(slug) ? input.get(slug) : handler.defaultValue(field);
            if (value == null) {
                if (field.isRequired()) violations.add(slug + ": is required");
                continue;
            }
            try {
                handler.validate(field, value);
                values.put(field, value);
            } catch (IllegalArgumentException e) {
                violations.add(slug + ": " + e.getMessage());
            }
        }
        throwIfInvalid(violations);
        checkReferences(values);
        checkUnique(schema, values, null);

        Map<Field<?>, Object> row = new LinkedHashMap<>();
        row.put(DynamicRecordRepository.TITLE, command.getTitle());
        row.put(DynamicRecordRepository.STATUS, command.getStatus());
        row.put(DynamicRecordRepository.EXTRA, json(command.getExtra() != null ? command.getExtra() : Map.of()));
        row.put(DynamicRecordRepository.CREATED_BY, createdBy.name());
        row.putAll(toColumns(values));
        long id = dynamicRecordRepository.insert(schema.table(), row);
        return toDto(schema, load(schema, id));
    }

    @Transactional
    public RecordDto updateRecord(Long collectionId, Long recordId, UpdateRecordCommand command) {
        Schema schema = schema(collectionId);
        Record current = load(schema, recordId);
        Map<String, Object> input = command.getValues() != null ? command.getValues() : Map.of();
        List<String> violations = new ArrayList<>(unknownKeys(schema, input));

        Map<CollectionField, Object> changes = new LinkedHashMap<>();
        for (CollectionField field : schema.fields()) {
            String slug = field.getSlug().value();
            if (!input.containsKey(slug)) {
                continue;
            }
            FieldTypeHandler handler = handler(field);
            Object value = input.get(slug);
            if (value == null) {
                if (field.isRequired()) violations.add(slug + ": is required");
                else changes.put(field, null);
                continue;
            }
            try {
                handler.validate(field, value);
                handler.validateChange(field, handler.fromDb(field, current), value);
                changes.put(field, value);
            } catch (IllegalArgumentException e) {
                violations.add(slug + ": " + e.getMessage());
            }
        }
        throwIfInvalid(violations);
        checkReferences(changes);
        checkUnique(schema, changes, recordId);

        Map<Field<?>, Object> row = new LinkedHashMap<>();
        if (command.getTitle() != null) row.put(DynamicRecordRepository.TITLE, command.getTitle());
        if (command.getStatus() != null) row.put(DynamicRecordRepository.STATUS, command.getStatus());
        if (command.getExtra() != null) row.put(DynamicRecordRepository.EXTRA, json(command.getExtra()));
        row.putAll(toColumns(changes));
        if (!row.isEmpty()) {
            dynamicRecordRepository.update(schema.table(), recordId, row);
        }
        return toDto(schema, load(schema, recordId));
    }

    @Transactional
    public void deleteRecord(Long collectionId, Long recordId) {
        Schema schema = schema(collectionId);
        if (!dynamicRecordRepository.delete(schema.table(), recordId)) {
            throw new RecordNotFoundException(collectionId, recordId);
        }
    }

    private Schema schema(Long collectionId) {
        Collection collection = collectionRepository.findById(new Collection.CollectionId(collectionId))
                .filter(candidate -> candidate.getArchivedAt() == null)
                .orElseThrow(() -> new CollectionNotFoundException(collectionId));
        List<CollectionField> fields = collection.getFields().stream()
                .filter(field -> field.getArchivedAt() == null)
                .sorted(Comparator.comparingInt(CollectionField::getPosition))
                .toList();
        return new Schema(collection, fields);
    }

    private Record load(Schema schema, Long recordId) {
        return dynamicRecordRepository.findById(schema.table(), columns(schema), recordId)
                .orElseThrow(() -> new RecordNotFoundException(schema.id(), recordId));
    }

    private FieldTypeHandler handler(CollectionField field) {
        return fieldTypeRegistry.handler(field.getType());
    }

    private List<Field<?>> columns(Schema schema) {
        List<Field<?>> columns = new ArrayList<>();
        for (CollectionField field : schema.fields()) {
            handler(field).columns(field).forEach(column -> columns.add(DSL.field(DSL.name(column.name()), column.dataType())));
        }
        return columns;
    }

    /** Typed column → database value for the given API values. */
    private Map<Field<?>, Object> toColumns(Map<CollectionField, Object> values) {
        Map<Field<?>, Object> columns = new LinkedHashMap<>();
        values.forEach((field, value) -> {
            FieldTypeHandler handler = handler(field);
            Map<String, Object> dbValues = handler.toDb(field, value);
            for (ColumnSpec column : handler.columns(field)) {
                columns.put(DSL.field(DSL.name(column.name()), column.dataType()), dbValues.get(column.name()));
            }
        });
        return columns;
    }

    private static List<String> unknownKeys(Schema schema, Map<String, Object> input) {
        return input.keySet().stream()
                .filter(key -> schema.field(key) == null)
                .sorted()
                .map(key -> key + ": is not a field of this collection")
                .toList();
    }

    private static void throwIfInvalid(List<String> violations) {
        if (!violations.isEmpty()) {
            throw new RecordValidationException(violations);
        }
    }

    /** Referenced records must exist (the foreign key would reject them too, but without a usable message). */
    private void checkReferences(Map<CollectionField, Object> values) {
        List<String> violations = new ArrayList<>();
        values.forEach((field, value) -> {
            if (field.getType() != FieldType.RELATION || value == null) {
                return;
            }
            Long targetId = RelationFieldTypeHandler.targetCollectionId(field);
            Collection target = collectionRepository.findById(new Collection.CollectionId(targetId))
                    .orElseThrow(() -> new CollectionNotFoundException(targetId));
            long recordId = ((Number) value).longValue();
            if (!dynamicRecordRepository.exists(target.getTableName(), recordId)) {
                violations.add(field.getSlug().value() + ": record " + recordId + " does not exist in '" + target.getSlug().value() + "'");
            }
        });
        throwIfInvalid(violations);
    }

    private void checkUnique(Schema schema, Map<CollectionField, Object> values, Long excludeRecordId) {
        values.forEach((field, value) -> {
            if (!field.isUniqueValue() || value == null) {
                return;
            }
            FieldTypeHandler handler = handler(field);
            ColumnSpec column = handler.columns(field).getFirst();
            Object dbValue = handler.toDb(field, value).get(column.name());
            if (dynamicRecordRepository.existsWithValue(schema.table(), DSL.field(DSL.name(column.name()), column.dataType()),
                    dbValue, excludeRecordId)) {
                throw new DuplicateResourceException("Another record already has this value for '" + field.getSlug().value() + "'");
            }
        });
    }

    private Condition parseFilter(Schema schema, String raw) {
        String prefix = "Invalid filter '" + raw + "': ";
        String[] parts = raw.split(":", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException(prefix + "expected field:operator[:value]");
        }
        Target target = target(schema, parts[0], prefix);
        Field<Object> column = target.column();
        String operator = parts[1];
        switch (operator) {
            case "empty":
                return column.isNull();
            case "notempty":
                return column.isNotNull();
            case "eq", "ne", "lt", "lte", "gt", "gte", "contains":
                break;
            default:
                throw new IllegalArgumentException(prefix + "unknown operator '" + operator + "'");
        }
        if (parts.length < 3) {
            throw new IllegalArgumentException(prefix + "missing value");
        }
        if (operator.equals("contains")) {
            if (!target.text()) {
                throw new IllegalArgumentException(prefix + "contains only works on text fields");
            }
            return column.cast(String.class).containsIgnoreCase(parts[2]);
        }
        Object value;
        try {
            value = target.parser().apply(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(prefix + e.getMessage());
        }
        Field<Object> bound = DSL.val(value, column);
        return switch (operator) {
            case "eq" -> column.eq(bound);
            case "ne" -> column.isDistinctFrom(bound);
            case "lt" -> column.lt(bound);
            case "lte" -> column.le(bound);
            case "gt" -> column.gt(bound);
            default -> column.ge(bound);
        };
    }

    /** The title or any text column contains {@code query} (jOOQ escapes LIKE wildcards in containsIgnoreCase). */
    private Condition searchCondition(Schema schema, String query) {
        Condition any = DynamicRecordRepository.TITLE.containsIgnoreCase(query);
        for (CollectionField field : schema.fields()) {
            for (ColumnSpec column : handler(field).columns(field)) {
                if (column.dataType().getType() == String.class) {
                    any = any.or(DSL.field(DSL.name(column.name()), String.class).containsIgnoreCase(query));
                }
            }
        }
        return any;
    }

    private SortField<?> parseSort(Schema schema, String sort) {
        if (sort == null || sort.isBlank()) {
            return DynamicRecordRepository.ID.desc();
        }
        boolean descending = sort.startsWith("-");
        Field<Object> column = target(schema, descending ? sort.substring(1) : sort, "Invalid sort '" + sort + "': ").column();
        return descending ? column.desc() : column.asc();
    }

    @SuppressWarnings("unchecked")
    private Target target(Schema schema, String name, String errorPrefix) {
        switch (name) {
            case "id":
                return new Target((Field<Object>) (Field<?>) DynamicRecordRepository.ID, RecordService::parseId, false);
            case "title":
                return new Target((Field<Object>) (Field<?>) DynamicRecordRepository.TITLE, raw -> raw, true);
            case "status":
                return new Target((Field<Object>) (Field<?>) DynamicRecordRepository.STATUS, raw -> raw, true);
            case "createdAt":
                return new Target((Field<Object>) (Field<?>) DynamicRecordRepository.CREATED_AT, RecordService::parseDateTime, false);
            case "lastModified":
                return new Target((Field<Object>) (Field<?>) DynamicRecordRepository.LAST_MODIFIED, RecordService::parseDateTime, false);
            default:
                break;
        }
        CollectionField field = schema.field(name);
        if (field == null) {
            throw new IllegalArgumentException(errorPrefix + "unknown field '" + name + "'");
        }
        FieldTypeHandler handler = handler(field);
        ColumnSpec column = handler.columns(field).getFirst();
        Field<Object> typed = (Field<Object>) DSL.field(DSL.name(column.name()), column.dataType());
        return new Target(typed, raw -> handler.parseFilterValue(field, raw), column.dataType().getType() == String.class);
    }

    private static Object parseId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("must be a record id");
        }
    }

    private static Object parseDateTime(String raw) {
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("must be an ISO-8601 date-time with offset");
        }
    }

    private RecordDto toDto(Schema schema, Record record) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (CollectionField field : schema.fields()) {
            values.put(field.getSlug().value(), handler(field).fromDb(field, record));
        }
        JSONB extra = record.get(DynamicRecordRepository.EXTRA);
        return new RecordDto()
                .id(record.get(DynamicRecordRepository.ID))
                .collectionId(schema.id())
                .title(record.get(DynamicRecordRepository.TITLE))
                .status(record.get(DynamicRecordRepository.STATUS))
                .values(values)
                .extra(extra != null ? objectMapper.readValue(extra.data(), MAP) : new LinkedHashMap<>())
                .createdAt(record.get(DynamicRecordRepository.CREATED_AT))
                .lastModified(record.get(DynamicRecordRepository.LAST_MODIFIED))
                .createdBy(record.get(DynamicRecordRepository.CREATED_BY));
    }

    private JSONB json(Map<String, Object> value) {
        return JSONB.valueOf(objectMapper.writeValueAsString(value));
    }
}
