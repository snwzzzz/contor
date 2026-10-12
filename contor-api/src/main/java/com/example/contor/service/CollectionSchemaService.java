package com.example.contor.service;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.api.model.UpdateCollectionCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.CollectionSchemaChange;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.DisplayConfig;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.Slug;
import com.example.contor.exception.CollectionFieldNotFoundException;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.fieldtype.ColumnSpec;
import com.example.contor.fieldtype.FieldTypeHandler;
import com.example.contor.fieldtype.FieldTypeRegistry;
import com.example.contor.fieldtype.RelationFieldTypeHandler;
import com.example.contor.mapper.CollectionMapper;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionFieldRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.CollectionSchemaChangeRepository;
import com.example.contor.persistence.dynamic.CollectionTableDdl;
import com.example.contor.persistence.dynamic.DynamicNames;
import com.example.contor.security.CurrentActor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collections and their fields: metadata in {@code collection}/{@code collection_field}, the generated table via
 * {@link CollectionTableDdl}. Every schema change bumps {@code schema_version}, writes {@code collection_schema_change}
 * and the audit log, all in one transaction together with the DDL. Input is fully validated before anything is written.
 * Access control (OWNER only) is enforced by the controller, so the seeder and proposal appliers can use this service.
 */
@RequiredArgsConstructor
@Service
public class CollectionSchemaService {

    /** Columns every generated table has; fields cannot use these names. */
    static final Set<String> SYSTEM_COLUMNS = Set.of("id", "title", "status", "extra", "created_at", "last_modified", "created_by");

    private static final String NO_DDL = "-- no DDL";

    private final CollectionRepository collectionRepository;
    private final CollectionFieldRepository collectionFieldRepository;
    private final CollectionSchemaChangeRepository collectionSchemaChangeRepository;
    private final AppUserRepository appUserRepository;
    private final CollectionTableDdl collectionTableDdl;
    private final FieldTypeRegistry fieldTypeRegistry;
    private final AuditService auditService;

    /** A validated, not yet saved field plus what is needed to create its columns. */
    private record PreparedField(CollectionField field, FieldTypeHandler handler, String targetTable) {
    }

    @Transactional
    public List<CollectionDto> findAll() {
        return collectionRepository.findByArchivedAtIsNullOrderByNameAsc().stream()
                .map(CollectionMapper::toDto)
                .toList();
    }

    @Transactional
    public CollectionDto findById(Long id) {
        return CollectionMapper.toDto(load(id));
    }

    @Transactional
    public CollectionDto createCollection(NewCollectionCommand command) {
        return create(command, false, CreatedBy.USER);
    }

    /** Collections contor ships with (People, Organizations, Events, Tasks). */
    @Transactional
    public CollectionDto createSystemCollection(NewCollectionCommand command) {
        return create(command, true, CreatedBy.USER);
    }

    /** Collections the AI discovered in the inbox (applied CREATE_COLLECTION proposals or AUTO permission). */
    @Transactional
    public CollectionDto createAiCollection(NewCollectionCommand command) {
        return create(command, false, CreatedBy.AI);
    }

    @Transactional
    public CollectionDto updateCollection(Long id, UpdateCollectionCommand command) {
        Collection collection = load(id);
        List<String> matchKeys = command.getMatchKeys() != null ? command.getMatchKeys() : collection.getMatchKeys();
        DisplayConfig displayConfig = command.getDisplayConfig() != null
                ? CollectionMapper.toDisplayConfig(command.getDisplayConfig())
                : collection.getDisplayConfig();
        validateConfig(matchKeys, displayConfig, activeFields(collection));

        if (command.getName() != null) {
            collection.setName(command.getName());
        }
        if (command.getDescription() != null) {
            collection.setDescription(command.getDescription());
        }
        if (command.getIcon() != null) {
            collection.setIcon(command.getIcon());
        }
        collection.setMatchKeys(new ArrayList<>(matchKeys));
        collection.setDisplayConfig(displayConfig);
        Collection saved = collectionRepository.saveAndFlush(collection);

        auditService.record(AuditEvent.currentActor(AuditAction.COLLECTION_UPDATED)
                .entity("collection", saved.getId().id())
                .after(snapshot(saved)));
        return CollectionMapper.toDto(saved);
    }

    @Transactional
    public CollectionDto addField(Long collectionId, NewCollectionFieldCommand command) {
        Collection collection = load(collectionId);
        Set<String> usedSlugs = new HashSet<>();
        Set<String> usedColumns = new HashSet<>();
        int position = 0;
        for (CollectionField existing : collection.getFields()) {
            usedSlugs.add(existing.getSlug().value());
            fieldTypeRegistry.handler(existing.getType()).columns(existing).forEach(column -> usedColumns.add(column.name()));
            position = Math.max(position, existing.getPosition());
        }
        PreparedField prepared = prepareField(command, position + 1, usedSlugs, usedColumns);

        List<String> ddl = persistField(collection, prepared);
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("operation", "ADD_FIELD");
        change.put("field", summary(prepared.field()));
        bumpVersion(collection, change, ddl);

        auditService.record(AuditEvent.currentActor(AuditAction.COLLECTION_FIELD_ADDED)
                .entity("collection", collection.getId().id())
                .after(change));
        return CollectionMapper.toDto(collection);
    }

    /** Hides the field and makes it read-only; the column and its data are kept. References in match keys/display config are removed. */
    @Transactional
    public CollectionDto archiveField(Long collectionId, Long fieldId) {
        Collection collection = load(collectionId);
        CollectionField field = collection.getFields().stream()
                .filter(candidate -> candidate.getId() != null && candidate.getId().id().equals(fieldId))
                .findFirst()
                .orElseThrow(() -> new CollectionFieldNotFoundException(fieldId));
        if (field.getArchivedAt() != null) {
            return CollectionMapper.toDto(collection);
        }

        field.setArchivedAt(Instant.now());
        collectionFieldRepository.saveAndFlush(field);
        String slug = field.getSlug().value();
        collection.setMatchKeys(collection.getMatchKeys().stream().filter(key -> !key.equals(slug)).toList());
        DisplayConfig config = collection.getDisplayConfig();
        if (config != null) {
            collection.setDisplayConfig(new DisplayConfig(config.defaultView(),
                    slug.equals(config.kanbanStatusField()) ? null : config.kanbanStatusField(),
                    slug.equals(config.calendarDateField()) ? null : config.calendarDateField(),
                    slug.equals(config.titleField()) ? null : config.titleField()));
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("operation", "ARCHIVE_FIELD");
        change.put("field", slug);
        bumpVersion(collection, change, List.of());

        auditService.record(AuditEvent.currentActor(AuditAction.COLLECTION_FIELD_ARCHIVED)
                .entity("collection", collection.getId().id())
                .after(change));
        return CollectionMapper.toDto(collection);
    }

    private CollectionDto create(NewCollectionCommand command, boolean system, CreatedBy createdBy) {
        Slug slug = new Slug(command.getSlug());
        String tableName = DynamicNames.tableName(slug);
        if (collectionRepository.existsBySlug(slug) || collectionRepository.existsByTableName(tableName)) {
            throw new DuplicateResourceException("A collection with slug '" + slug.value() + "' already exists");
        }

        List<PreparedField> prepared = new ArrayList<>();
        Set<String> usedSlugs = new HashSet<>();
        Set<String> usedColumns = new HashSet<>();
        List<NewCollectionFieldCommand> fieldCommands = command.getFields() != null ? command.getFields() : List.of();
        for (int i = 0; i < fieldCommands.size(); i++) {
            prepared.add(prepareField(fieldCommands.get(i), i + 1, usedSlugs, usedColumns));
        }
        List<String> matchKeys = command.getMatchKeys() != null ? command.getMatchKeys() : List.of();
        DisplayConfig displayConfig = command.getDisplayConfig() != null
                ? CollectionMapper.toDisplayConfig(command.getDisplayConfig())
                : new DisplayConfig(null, null, null, null);
        validateConfig(matchKeys, displayConfig, prepared.stream().map(PreparedField::field).toList());

        Collection collection = collectionRepository.saveAndFlush(Collection.builder()
                .slug(slug)
                .name(command.getName())
                .description(command.getDescription())
                .icon(command.getIcon())
                .tableName(tableName)
                .system(system)
                .matchKeys(new ArrayList<>(matchKeys))
                .displayConfig(displayConfig)
                .schemaVersion(1)
                .createdBy(createdBy)
                .fields(new ArrayList<>())
                .build());

        List<String> ddl = new ArrayList<>(collectionTableDdl.createTable(tableName));
        for (PreparedField field : prepared) {
            ddl.addAll(persistField(collection, field));
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("operation", "CREATE_COLLECTION");
        change.put("slug", slug.value());
        change.put("fields", prepared.stream().map(field -> summary(field.field())).toList());
        recordChange(collection, 0, 1, change, ddl);

        auditService.record(AuditEvent.currentActor(AuditAction.COLLECTION_CREATED)
                .entity("collection", collection.getId().id())
                .after(snapshot(collection)));
        return CollectionMapper.toDto(collection);
    }

    private PreparedField prepareField(NewCollectionFieldCommand command, int position, Set<String> usedSlugs, Set<String> usedColumns) {
        try {
            Slug slug = new Slug(command.getSlug());
            if (SYSTEM_COLUMNS.contains(slug.value())) {
                throw new IllegalArgumentException("slug is reserved for a system column");
            }
            if (usedSlugs.contains(slug.value())) {
                throw new IllegalArgumentException("slug is already used");
            }
            FieldType type = FieldType.valueOf(command.getType().name());
            FieldTypeHandler handler = fieldTypeRegistry.handler(type);
            Map<String, Object> options = command.getOptions() != null ? new LinkedHashMap<>(command.getOptions()) : new LinkedHashMap<>();
            handler.validateOptions(options);
            boolean unique = Boolean.TRUE.equals(command.getUniqueValue());
            if (unique && type == FieldType.MONEY) {
                throw new IllegalArgumentException("MONEY fields cannot be unique");
            }

            CollectionField field = CollectionField.builder()
                    .slug(slug)
                    .name(command.getName())
                    .description(command.getDescription())
                    .type(type)
                    .options(options)
                    .required(Boolean.TRUE.equals(command.getRequired()))
                    .uniqueValue(unique)
                    .position(position)
                    .columnName(slug.value())
                    .build();
            List<ColumnSpec> columns = handler.columns(field);
            for (ColumnSpec column : columns) {
                if (SYSTEM_COLUMNS.contains(column.name()) || usedColumns.contains(column.name())) {
                    throw new IllegalArgumentException("column '" + column.name() + "' is already used by another field");
                }
            }
            String targetTable = null;
            if (type == FieldType.RELATION) {
                Long targetId = RelationFieldTypeHandler.targetCollectionId(field);
                targetTable = collectionRepository.findById(new Collection.CollectionId(targetId))
                        .filter(target -> target.getArchivedAt() == null)
                        .map(Collection::getTableName)
                        .orElseThrow(() -> new IllegalArgumentException("target collection " + targetId + " does not exist"));
            }

            usedSlugs.add(slug.value());
            columns.forEach(column -> usedColumns.add(column.name()));
            return new PreparedField(field, handler, targetTable);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field '" + command.getSlug() + "': " + e.getMessage());
        }
    }

    private List<String> persistField(Collection collection, PreparedField prepared) {
        CollectionField field = prepared.field();
        field.setCollection(collection);
        CollectionField saved = collectionFieldRepository.saveAndFlush(field);
        collection.getFields().add(saved);

        String table = collection.getTableName();
        List<ColumnSpec> columns = prepared.handler().columns(saved);
        List<String> ddl = new ArrayList<>();
        for (ColumnSpec column : columns) {
            ddl.addAll(collectionTableDdl.addColumn(table, column.name(), column.dataType()));
            if (column.referencedCollectionId() != null) {
                ddl.addAll(collectionTableDdl.addForeignKey(table, column.name(), prepared.targetTable()));
            }
        }
        if (saved.isUniqueValue()) {
            ddl.addAll(collectionTableDdl.addUniqueIndex(table, columns.getFirst().name()));
        }
        return ddl;
    }

    private void validateConfig(List<String> matchKeys, DisplayConfig config, List<CollectionField> fields) {
        Map<String, CollectionField> bySlug = new LinkedHashMap<>();
        fields.forEach(field -> bySlug.put(field.getSlug().value(), field));
        for (String key : matchKeys) {
            if (!bySlug.containsKey(key)) {
                throw new IllegalArgumentException("matchKeys: '" + key + "' is not a field of this collection");
            }
        }
        if (config == null) {
            return;
        }
        requireField("displayConfig.titleField", config.titleField(), bySlug, null);
        requireField("displayConfig.kanbanStatusField", config.kanbanStatusField(), bySlug, Set.of(FieldType.STATUS));
        requireField("displayConfig.calendarDateField", config.calendarDateField(), bySlug, Set.of(FieldType.DATE, FieldType.DATETIME));
    }

    private static void requireField(String property, String slug, Map<String, CollectionField> bySlug, Set<FieldType> allowedTypes) {
        if (slug == null) {
            return;
        }
        CollectionField field = bySlug.get(slug);
        if (field == null) {
            throw new IllegalArgumentException(property + ": '" + slug + "' is not a field of this collection");
        }
        if (allowedTypes != null && !allowedTypes.contains(field.getType())) {
            String types = allowedTypes.size() == 1 ? "a STATUS" : "a DATE or DATETIME";
            throw new IllegalArgumentException(property + ": '" + slug + "' must be " + types + " field");
        }
    }

    private void bumpVersion(Collection collection, Map<String, Object> change, List<String> ddl) {
        int from = collection.getSchemaVersion();
        collection.setSchemaVersion(from + 1);
        collectionRepository.saveAndFlush(collection);
        recordChange(collection, from, from + 1, change, ddl);
    }

    private void recordChange(Collection collection, int fromVersion, int toVersion, Map<String, Object> change, List<String> ddl) {
        AppUser executedBy = CurrentActor.userId()
                .map(id -> appUserRepository.getReferenceById(new AppUser.AppUserId(id)))
                .orElse(null);
        collectionSchemaChangeRepository.saveAndFlush(CollectionSchemaChange.builder()
                .collection(collection)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .change(change)
                .ddlExecuted(ddl.isEmpty() ? NO_DDL : String.join(";\n", ddl) + ";")
                .executedAt(Instant.now())
                .executedBy(executedBy)
                .build());
    }

    private Collection load(Long id) {
        return collectionRepository.findById(new Collection.CollectionId(id))
                .orElseThrow(() -> new CollectionNotFoundException(id));
    }

    private static List<CollectionField> activeFields(Collection collection) {
        return collection.getFields().stream().filter(field -> field.getArchivedAt() == null).toList();
    }

    private static Map<String, Object> summary(CollectionField field) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("slug", field.getSlug().value());
        summary.put("name", field.getName());
        summary.put("type", field.getType().name());
        summary.put("options", field.getOptions());
        summary.put("required", field.isRequired());
        summary.put("uniqueValue", field.isUniqueValue());
        return summary;
    }

    private static Map<String, Object> snapshot(Collection collection) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("slug", collection.getSlug().value());
        snapshot.put("name", collection.getName());
        snapshot.put("schemaVersion", collection.getSchemaVersion());
        snapshot.put("matchKeys", collection.getMatchKeys());
        snapshot.put("fields", collection.getFields().stream().filter(field -> Objects.isNull(field.getArchivedAt()))
                .map(field -> field.getSlug().value()).toList());
        return snapshot;
    }
}
