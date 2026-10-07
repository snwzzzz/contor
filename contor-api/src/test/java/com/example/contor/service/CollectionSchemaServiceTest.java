package com.example.contor.service;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.DisplayConfigDto;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.api.model.UpdateCollectionCommand;
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
import com.example.contor.fieldtype.TestFieldTypes;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionFieldRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.CollectionSchemaChangeRepository;
import com.example.contor.persistence.dynamic.CollectionTableDdl;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CollectionSchemaServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private CollectionFieldRepository collectionFieldRepository;

    @Mock
    private CollectionSchemaChangeRepository collectionSchemaChangeRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private CollectionTableDdl collectionTableDdl;

    @Mock
    private AuditService auditService;

    private CollectionSchemaService service;

    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setup() {
        service = new CollectionSchemaService(collectionRepository, collectionFieldRepository, collectionSchemaChangeRepository,
                appUserRepository, collectionTableDdl, TestFieldTypes.registry(), auditService);
        lenient().when(collectionRepository.saveAndFlush(any(Collection.class))).thenAnswer(invocation -> {
            Collection collection = invocation.getArgument(0);
            if (collection.getId() == null) collection.setId(new Collection.CollectionId(ids.incrementAndGet()));
            return collection;
        });
        lenient().when(collectionFieldRepository.saveAndFlush(any(CollectionField.class))).thenAnswer(invocation -> {
            CollectionField field = invocation.getArgument(0);
            if (field.getId() == null) field.setId(new CollectionField.CollectionFieldId(ids.incrementAndGet()));
            return field;
        });
        lenient().when(collectionTableDdl.createTable(anyString())).thenReturn(List.of("create table"));
        lenient().when(collectionTableDdl.addColumn(anyString(), anyString(), any())).thenReturn(List.of("alter table"));
    }

    private static NewCollectionFieldCommand field(String slug, CollectionFieldType type) {
        return new NewCollectionFieldCommand(slug, slug, type);
    }

    private Collection existing(String slug, int schemaVersion) {
        var collection = Collection.builder().id(new Collection.CollectionId(1L)).slug(new Slug(slug)).name(slug)
                .tableName("c_" + slug).schemaVersion(schemaVersion).createdBy(CreatedBy.USER).fields(new ArrayList<>()).build();
        return collection;
    }

    private CollectionField existingField(Collection collection, long id, String slug, FieldType type, int position) {
        var field = CollectionField.builder().id(new CollectionField.CollectionFieldId(id)).collection(collection)
                .slug(new Slug(slug)).name(slug).type(type).position(position).columnName(slug).options(Map.of()).build();
        collection.getFields().add(field);
        return field;
    }

    @Test
    void createCollection() {
        var command = new NewCollectionCommand("deliveries", "Deliveries")
                .fields(List.of(
                        field("tracking", CollectionFieldType.TEXT).uniqueValue(true).required(true),
                        field("price", CollectionFieldType.MONEY),
                        field("state", CollectionFieldType.STATUS).options(Map.of("states", List.of("OPEN", "DONE")))))
                .matchKeys(List.of("tracking"))
                .displayConfig(new DisplayConfigDto().kanbanStatusField("state"));

        CollectionDto result = service.createCollection(command);

        assertEquals("deliveries", result.getSlug());
        assertEquals(1, result.getSchemaVersion());
        assertFalse(result.getSystem());
        assertEquals(List.of("tracking", "price", "state"), result.getFields().stream().map(f -> f.getSlug()).toList());
        assertEquals(List.of(1, 2, 3), result.getFields().stream().map(f -> f.getPosition()).toList());
        verify(collectionTableDdl).createTable("c_deliveries");
        verify(collectionTableDdl).addColumn("c_deliveries", "tracking", SQLDataType.CLOB);
        verify(collectionTableDdl).addColumn("c_deliveries", "price_amount", SQLDataType.NUMERIC(19, 4));
        verify(collectionTableDdl).addColumn("c_deliveries", "price_currency", SQLDataType.CHAR(3));
        verify(collectionTableDdl).addUniqueIndex("c_deliveries", "tracking");
        var change = ArgumentCaptor.forClass(CollectionSchemaChange.class);
        verify(collectionSchemaChangeRepository).saveAndFlush(change.capture());
        assertEquals(0, change.getValue().getFromVersion());
        assertEquals(1, change.getValue().getToVersion());
        assertEquals("CREATE_COLLECTION", change.getValue().getChange().get("operation"));
        assertTrue(change.getValue().getDdlExecuted().contains("create table"));
        verify(auditService).record(argThat(e -> e.action() == AuditAction.COLLECTION_CREATED && e.entityId().equals(result.getId())));
    }

    @Test
    void createSystemCollection_MarkedAsSystem() {
        CollectionDto result = service.createSystemCollection(new NewCollectionCommand("people", "People"));

        assertTrue(result.getSystem());
    }

    @Test
    void createCollection_DuplicateSlugRejected() {
        when(collectionRepository.existsBySlug(new Slug("deliveries"))).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.createCollection(new NewCollectionCommand("deliveries", "Deliveries")));
        verify(collectionTableDdl, never()).createTable(any());
    }

    @Test
    void createCollection_InvalidSlugRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createCollection(new NewCollectionCommand("Bad Slug", "x")));
        verify(collectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCollection_SystemColumnSlugRejected() {
        var command = new NewCollectionCommand("deliveries", "Deliveries").fields(List.of(field("title", CollectionFieldType.TEXT)));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.createCollection(command));
        assertEquals("Field 'title': slug is reserved for a system column", exception.getMessage());
        verify(collectionTableDdl, never()).createTable(any());
    }

    @Test
    void createCollection_DuplicateFieldSlugRejected() {
        var command = new NewCollectionCommand("deliveries", "Deliveries")
                .fields(List.of(field("carrier", CollectionFieldType.TEXT), field("carrier", CollectionFieldType.TEXT)));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.createCollection(command));
        assertEquals("Field 'carrier': slug is already used", exception.getMessage());
    }

    @Test
    void createCollection_InvalidOptionsRejectedBeforeAnyDdl() {
        var command = new NewCollectionCommand("deliveries", "Deliveries").fields(List.of(field("carrier", CollectionFieldType.ENUM)));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.createCollection(command));
        assertEquals("Field 'carrier': ENUM options need a non-empty list 'values'", exception.getMessage());
        verify(collectionTableDdl, never()).createTable(any());
        verify(collectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCollection_MoneyCannotBeUnique() {
        var command = new NewCollectionCommand("deliveries", "Deliveries")
                .fields(List.of(field("price", CollectionFieldType.MONEY).uniqueValue(true)));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.createCollection(command));
        assertEquals("Field 'price': MONEY fields cannot be unique", exception.getMessage());
    }

    @Test
    void createCollection_MatchKeysMustReferenceFields() {
        var command = new NewCollectionCommand("deliveries", "Deliveries")
                .fields(List.of(field("tracking", CollectionFieldType.TEXT))).matchKeys(List.of("order_no"));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.createCollection(command));
        assertEquals("matchKeys: 'order_no' is not a field of this collection", exception.getMessage());
    }

    @Test
    void createCollection_KanbanFieldMustBeStatus() {
        var command = new NewCollectionCommand("deliveries", "Deliveries")
                .fields(List.of(field("tracking", CollectionFieldType.TEXT)))
                .displayConfig(new DisplayConfigDto().kanbanStatusField("tracking"));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.createCollection(command));
        assertEquals("displayConfig.kanbanStatusField: 'tracking' must be a STATUS field", exception.getMessage());
    }

    @Test
    void createCollection_CalendarFieldMustBeDateOrDateTime() {
        var command = new NewCollectionCommand("deliveries", "Deliveries")
                .fields(List.of(field("tracking", CollectionFieldType.TEXT)))
                .displayConfig(new DisplayConfigDto().calendarDateField("tracking"));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.createCollection(command));
        assertEquals("displayConfig.calendarDateField: 'tracking' must be a DATE or DATETIME field", exception.getMessage());
    }

    @Test
    void createCollection_RelationAddsForeignKeyToTargetTable() {
        var organizations = existing("organizations", 1);
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(organizations));
        var command = new NewCollectionCommand("people", "People")
                .fields(List.of(field("employer", CollectionFieldType.RELATION).options(Map.of("targetCollectionId", 1))));

        service.createCollection(command);

        verify(collectionTableDdl).addColumn("c_people", "employer", SQLDataType.BIGINT);
        verify(collectionTableDdl).addForeignKey("c_people", "employer", "c_organizations");
    }

    @Test
    void createCollection_RelationToUnknownCollectionRejected() {
        when(collectionRepository.findById(new Collection.CollectionId(99L))).thenReturn(Optional.empty());
        var command = new NewCollectionCommand("people", "People")
                .fields(List.of(field("employer", CollectionFieldType.RELATION).options(Map.of("targetCollectionId", 99))));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.createCollection(command));
        assertEquals("Field 'employer': target collection 99 does not exist", exception.getMessage());
        verify(collectionTableDdl, never()).createTable(any());
    }

    @Test
    void addField() {
        var collection = existing("deliveries", 3);
        existingField(collection, 10L, "carrier", FieldType.TEXT, 1);
        existingField(collection, 11L, "eta", FieldType.DATE, 4);
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(collection));

        CollectionDto result = service.addField(1L, field("weight", CollectionFieldType.NUMBER).description("kg"));

        assertEquals(4, result.getSchemaVersion());
        var added = result.getFields().stream().filter(f -> f.getSlug().equals("weight")).findFirst().orElseThrow();
        assertEquals(5, added.getPosition());
        assertEquals("kg", added.getDescription());
        verify(collectionTableDdl).addColumn("c_deliveries", "weight", SQLDataType.NUMERIC);
        verify(collectionSchemaChangeRepository).saveAndFlush(argThat(c -> c.getFromVersion() == 3 && c.getToVersion() == 4
                && "ADD_FIELD".equals(c.getChange().get("operation"))));
        verify(auditService).record(argThat(e -> e.action() == AuditAction.COLLECTION_FIELD_ADDED));
    }

    @Test
    void addField_ColumnCollisionRejected() {
        var collection = existing("deliveries", 2);
        existingField(collection, 10L, "price", FieldType.MONEY, 1);
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(collection));

        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.addField(1L, field("price_amount", CollectionFieldType.NUMBER)));
        assertEquals("Field 'price_amount': column 'price_amount' is already used by another field", exception.getMessage());
        verify(collectionTableDdl, never()).addColumn(any(), any(), any());
    }

    @Test
    void addField_SlugOfArchivedFieldStaysTaken() {
        var collection = existing("deliveries", 2);
        existingField(collection, 10L, "carrier", FieldType.TEXT, 1).setArchivedAt(java.time.Instant.now());
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(collection));

        assertThrows(IllegalArgumentException.class, () -> service.addField(1L, field("carrier", CollectionFieldType.TEXT)));
    }

    @Test
    void addField_CollectionNotFound() {
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.empty());

        assertThrows(CollectionNotFoundException.class, () -> service.addField(1L, field("weight", CollectionFieldType.NUMBER)));
    }

    @Test
    void archiveField() {
        var collection = existing("deliveries", 2);
        existingField(collection, 10L, "tracking", FieldType.TEXT, 1);
        existingField(collection, 11L, "state", FieldType.STATUS, 2);
        collection.setMatchKeys(new ArrayList<>(List.of("tracking")));
        collection.setDisplayConfig(new DisplayConfig("kanban", "state", null, "tracking"));
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(collection));

        CollectionDto result = service.archiveField(1L, 10L);

        assertNotNull(result.getFields().get(0).getArchivedAt());
        assertEquals(3, result.getSchemaVersion());
        assertEquals(List.of(), result.getMatchKeys());
        assertNull(result.getDisplayConfig().getTitleField());
        assertEquals("state", result.getDisplayConfig().getKanbanStatusField());
        verify(collectionSchemaChangeRepository).saveAndFlush(argThat(c -> "ARCHIVE_FIELD".equals(c.getChange().get("operation"))));
        verify(collectionTableDdl, never()).addColumn(any(), any(), any());
        verify(auditService).record(argThat(e -> e.action() == AuditAction.COLLECTION_FIELD_ARCHIVED));
    }

    @Test
    void archiveField_FieldOfAnotherCollectionNotFound() {
        var collection = existing("deliveries", 2);
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(collection));

        assertThrows(CollectionFieldNotFoundException.class, () -> service.archiveField(1L, 99L));
        verify(collectionSchemaChangeRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCollection_OnlyChangesGivenFields() {
        var collection = existing("deliveries", 2);
        collection.setDescription("Parcels");
        existingField(collection, 10L, "tracking", FieldType.TEXT, 1);
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(collection));

        CollectionDto result = service.updateCollection(1L, new UpdateCollectionCommand().name("Shipments").matchKeys(List.of("tracking")));

        assertEquals("Shipments", result.getName());
        assertEquals("Parcels", result.getDescription());
        assertEquals(List.of("tracking"), result.getMatchKeys());
        assertEquals(2, result.getSchemaVersion());
        verify(auditService).record(argThat(e -> e.action() == AuditAction.COLLECTION_UPDATED));
    }

    @Test
    void findAll() {
        when(collectionRepository.findByArchivedAtIsNullOrderByNameAsc()).thenReturn(List.of(existing("deliveries", 1)));

        assertEquals(1, service.findAll().size());
    }

    @Test
    void findById_NotFound() {
        when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.empty());

        assertThrows(CollectionNotFoundException.class, () -> service.findById(1L));
    }
}
