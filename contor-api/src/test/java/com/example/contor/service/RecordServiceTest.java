package com.example.contor.service;

import com.example.contor.api.model.NewRecordCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.api.model.UpdateRecordCommand;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.Slug;
import com.example.contor.exception.CollectionNotFoundException;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.exception.RecordValidationException;
import com.example.contor.fieldtype.TestFieldTypes;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.dynamic.DynamicRecordRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

    private static final String TABLE = "c_deliveries";

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private DynamicRecordRepository dynamicRecordRepository;

    private RecordService service;
    private Collection deliveries;

    @BeforeEach
    void setup() {
        service = new RecordService(collectionRepository, dynamicRecordRepository, TestFieldTypes.registry(), JsonMapper.builder().build());
        deliveries = Collection.builder().id(new Collection.CollectionId(1L)).slug(new Slug("deliveries")).name("Deliveries")
                .tableName(TABLE).createdBy(CreatedBy.USER).fields(new ArrayList<>()).build();
        addField(deliveries, "tracking", FieldType.TEXT, Map.of(), true, true);
        addField(deliveries, "eta", FieldType.DATE, Map.of(), false, false);
        addField(deliveries, "state", FieldType.STATUS,
                Map.of("states", List.of("ORDERED", "SHIPPED", "DELIVERED"), "transitions", Map.of("ORDERED", List.of("SHIPPED"))), false, false);
        addField(deliveries, "price", FieldType.MONEY, Map.of(), false, false);
        lenient().when(collectionRepository.findById(new Collection.CollectionId(1L))).thenReturn(Optional.of(deliveries));
    }

    private static CollectionField addField(Collection collection, String slug, FieldType type, Map<String, Object> options,
                                            boolean required, boolean unique) {
        var field = CollectionField.builder().id(new CollectionField.CollectionFieldId((long) collection.getFields().size() + 10))
                .collection(collection).slug(new Slug(slug)).name(slug).type(type).options(new HashMap<>(options))
                .required(required).uniqueValue(unique).position(collection.getFields().size() + 1).columnName(slug).build();
        collection.getFields().add(field);
        return field;
    }

    private static Record row(long id, Map<String, Object> columns) {
        var values = new java.util.LinkedHashMap<Field<?>, Object>();
        values.put(DynamicRecordRepository.ID, id);
        values.put(DynamicRecordRepository.TITLE, "Parcel");
        values.put(DynamicRecordRepository.STATUS, null);
        values.put(DynamicRecordRepository.EXTRA, JSONB.valueOf("{\"note\":\"fragile\"}"));
        values.put(DynamicRecordRepository.CREATED_AT, OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC));
        values.put(DynamicRecordRepository.LAST_MODIFIED, null);
        values.put(DynamicRecordRepository.CREATED_BY, "USER");
        columns.forEach((name, value) -> values.put(DSL.field(DSL.name(name)), value));
        Record record = DSL.using(SQLDialect.POSTGRES).newRecord(values.keySet().toArray(Field<?>[]::new));
        values.forEach((field, value) -> record.set((Field<Object>) field, value));
        return record;
    }

    private static Map<String, Object> storedColumns() {
        var columns = new HashMap<String, Object>();
        columns.put("tracking", "T-1");
        columns.put("eta", LocalDate.of(2026, 3, 1));
        columns.put("state", "ORDERED");
        columns.put("price_amount", new BigDecimal("9.9900"));
        columns.put("price_currency", "EUR");
        return columns;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureInsert() {
        ArgumentCaptor<Map<Field<?>, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dynamicRecordRepository).insert(eq(TABLE), captor.capture());
        var byName = new HashMap<String, Object>();
        captor.getValue().forEach((field, value) -> byName.put(field.getName(), value));
        return byName;
    }

    @Test
    void createRecord() {
        when(dynamicRecordRepository.insert(eq(TABLE), anyMap())).thenReturn(5L);
        when(dynamicRecordRepository.findById(eq(TABLE), any(), eq(5L))).thenReturn(Optional.of(row(5L, storedColumns())));

        RecordDto result = service.createRecord(1L, new NewRecordCommand()
                .title("Parcel")
                .values(Map.of("tracking", "T-1", "eta", "2026-03-01", "price", Map.of("amount", 9.99, "currency", "EUR")))
                .extra(Map.of("note", "fragile")));

        var inserted = captureInsert();
        assertEquals("T-1", inserted.get("tracking"));
        assertEquals(LocalDate.of(2026, 3, 1), inserted.get("eta"));
        assertEquals("ORDERED", inserted.get("state"), "STATUS defaults to its first state");
        assertEquals(new BigDecimal("9.99"), inserted.get("price_amount"));
        assertEquals("EUR", inserted.get("price_currency"));
        assertEquals("SYSTEM", inserted.get("created_by"), "no logged-in user in this test");
        assertTrue(((JSONB) inserted.get("extra")).data().contains("fragile"));

        assertEquals(5L, result.getId());
        assertEquals(1L, result.getCollectionId());
        assertEquals("2026-03-01", result.getValues().get("eta"));
        assertEquals(Map.of("amount", new BigDecimal("9.99"), "currency", "EUR"), result.getValues().get("price"));
        assertEquals("fragile", result.getExtra().get("note"));
    }

    @Test
    void createRecord_CollectsAllViolations() {
        var exception = assertThrows(RecordValidationException.class, () -> service.createRecord(1L, new NewRecordCommand()
                .values(Map.of("eta", "tomorrow", "state", "LOST", "colour", "red"))));

        assertEquals(List.of(
                "colour: is not a field of this collection",
                "tracking: is required",
                "eta: must be a date (YYYY-MM-DD)",
                "state: must be one of [ORDERED, SHIPPED, DELIVERED]"), exception.getViolations());
        verify(dynamicRecordRepository, never()).insert(any(), any());
    }

    @Test
    void createRecord_ArchivedFieldsAreNotWritable() {
        deliveries.getFields().get(1).setArchivedAt(Instant.now());

        var exception = assertThrows(RecordValidationException.class, () -> service.createRecord(1L, new NewRecordCommand()
                .values(Map.of("tracking", "T-1", "eta", "2026-03-01"))));
        assertEquals(List.of("eta: is not a field of this collection"), exception.getViolations());
    }

    @Test
    void createRecord_DuplicateUniqueValueRejected() {
        when(dynamicRecordRepository.existsWithValue(eq(TABLE), any(), eq("T-1"), isNull())).thenReturn(true);

        var exception = assertThrows(DuplicateResourceException.class,
                () -> service.createRecord(1L, new NewRecordCommand().values(Map.of("tracking", "T-1"))));
        assertEquals("Another record already has this value for 'tracking'", exception.getMessage());
        verify(dynamicRecordRepository, never()).insert(any(), any());
    }

    @Test
    void createRecord_RelationTargetMustExist() {
        var people = Collection.builder().id(new Collection.CollectionId(2L)).slug(new Slug("people")).name("People")
                .tableName("c_people").createdBy(CreatedBy.USER).fields(new ArrayList<>()).build();
        addField(deliveries, "recipient", FieldType.RELATION, Map.of("targetCollectionId", 2), false, false);
        when(collectionRepository.findById(new Collection.CollectionId(2L))).thenReturn(Optional.of(people));
        when(dynamicRecordRepository.exists("c_people", 42L)).thenReturn(false);

        var exception = assertThrows(RecordValidationException.class, () -> service.createRecord(1L,
                new NewRecordCommand().values(Map.of("tracking", "T-1", "recipient", 42))));
        assertEquals(List.of("recipient: record 42 does not exist in 'people'"), exception.getViolations());
    }

    @Test
    void createRecord_CollectionNotFound() {
        when(collectionRepository.findById(new Collection.CollectionId(9L))).thenReturn(Optional.empty());

        assertThrows(CollectionNotFoundException.class, () -> service.createRecord(9L, new NewRecordCommand()));
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateRecord_OnlyChangesGivenValues() {
        when(dynamicRecordRepository.findById(eq(TABLE), any(), eq(5L))).thenReturn(Optional.of(row(5L, storedColumns())));
        var values = new HashMap<String, Object>();
        values.put("state", "SHIPPED");
        values.put("eta", null);

        service.updateRecord(1L, 5L, new UpdateRecordCommand().values(values));

        ArgumentCaptor<Map<Field<?>, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dynamicRecordRepository).update(eq(TABLE), eq(5L), captor.capture());
        var byName = new HashMap<String, Object>();
        captor.getValue().forEach((field, value) -> byName.put(field.getName(), value));
        assertEquals(Map.of("state", "SHIPPED"), Map.of("state", byName.get("state")));
        assertTrue(byName.containsKey("eta"));
        assertNull(byName.get("eta"));
        assertFalse(byName.containsKey("tracking"));
        assertFalse(byName.containsKey("title"));
    }

    @Test
    void updateRecord_DisallowedStatusTransitionRejected() {
        when(dynamicRecordRepository.findById(eq(TABLE), any(), eq(5L))).thenReturn(Optional.of(row(5L, storedColumns())));

        var exception = assertThrows(RecordValidationException.class,
                () -> service.updateRecord(1L, 5L, new UpdateRecordCommand().values(Map.of("state", "DELIVERED"))));
        assertEquals(List.of("state: transition from 'ORDERED' to 'DELIVERED' is not allowed"), exception.getViolations());
        verify(dynamicRecordRepository, never()).update(any(), anyLong(), any());
    }

    @Test
    void updateRecord_RequiredFieldCannotBeCleared() {
        when(dynamicRecordRepository.findById(eq(TABLE), any(), eq(5L))).thenReturn(Optional.of(row(5L, storedColumns())));
        var values = new HashMap<String, Object>();
        values.put("tracking", null);

        var exception = assertThrows(RecordValidationException.class,
                () -> service.updateRecord(1L, 5L, new UpdateRecordCommand().values(values)));
        assertEquals(List.of("tracking: is required"), exception.getViolations());
    }

    @Test
    void updateRecord_UniqueCheckExcludesTheRecordItself() {
        when(dynamicRecordRepository.findById(eq(TABLE), any(), eq(5L))).thenReturn(Optional.of(row(5L, storedColumns())));

        service.updateRecord(1L, 5L, new UpdateRecordCommand().values(Map.of("tracking", "T-2")));

        verify(dynamicRecordRepository).existsWithValue(eq(TABLE), any(), eq("T-2"), eq(5L));
    }

    @Test
    void getRecord_NotFound() {
        when(dynamicRecordRepository.findById(eq(TABLE), any(), eq(5L))).thenReturn(Optional.empty());

        var exception = assertThrows(RecordNotFoundException.class, () -> service.getRecord(1L, 5L));
        assertEquals("Record 5 not found in collection 1", exception.getMessage());
    }

    @Test
    void deleteRecord() {
        when(dynamicRecordRepository.delete(TABLE, 5L)).thenReturn(true);

        service.deleteRecord(1L, 5L);

        verify(dynamicRecordRepository).delete(TABLE, 5L);
    }

    @Test
    void deleteRecord_NotFound() {
        when(dynamicRecordRepository.delete(TABLE, 5L)).thenReturn(false);

        assertThrows(RecordNotFoundException.class, () -> service.deleteRecord(1L, 5L));
    }

    @Test
    void listRecords() {
        when(dynamicRecordRepository.find(eq(TABLE), any(), any(Condition.class), any(SortField.class), eq(25), eq(25)))
                .thenReturn(List.of(row(5L, storedColumns())));
        when(dynamicRecordRepository.count(eq(TABLE), any(Condition.class))).thenReturn(26L);

        RecordPageDto page = service.listRecords(1L, List.of("eta:gte:2026-01-01", "tracking:contains:t-", "price:lt:10", "state:notempty"), "-eta", 1, 25);

        assertEquals(1, page.getItems().size());
        assertEquals(1, page.getPage());
        assertEquals(25, page.getSize());
        assertEquals(26L, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void listRecords_InvalidFiltersRejected() {
        assertEquals("Invalid filter 'nope:eq:1': unknown field 'nope'",
                assertThrows(IllegalArgumentException.class, () -> service.listRecords(1L, List.of("nope:eq:1"), null, 0, 25)).getMessage());
        assertEquals("Invalid filter 'eta:eq:soon': must be a date (YYYY-MM-DD)",
                assertThrows(IllegalArgumentException.class, () -> service.listRecords(1L, List.of("eta:eq:soon"), null, 0, 25)).getMessage());
        assertEquals("Invalid filter 'eta:like:x': unknown operator 'like'",
                assertThrows(IllegalArgumentException.class, () -> service.listRecords(1L, List.of("eta:like:x"), null, 0, 25)).getMessage());
        assertEquals("Invalid filter 'eta:contains:2026': contains only works on text fields",
                assertThrows(IllegalArgumentException.class, () -> service.listRecords(1L, List.of("eta:contains:2026"), null, 0, 25)).getMessage());
        assertEquals("Invalid filter 'eta': expected field:operator[:value]",
                assertThrows(IllegalArgumentException.class, () -> service.listRecords(1L, List.of("eta"), null, 0, 25)).getMessage());
        assertEquals("Invalid sort 'colour': unknown field 'colour'",
                assertThrows(IllegalArgumentException.class, () -> service.listRecords(1L, null, "colour", 0, 25)).getMessage());
        verify(dynamicRecordRepository, never()).find(any(), any(), any(), any(), anyInt(), anyInt());
    }
}
