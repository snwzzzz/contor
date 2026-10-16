package com.example.contor.service;

import com.example.contor.ai.CollectionPlan;
import com.example.contor.ai.EvidencePack.AnalysisInput;
import com.example.contor.ai.EvidencePack.AnalysisRow;
import com.example.contor.ai.EvidencePack.RowSource;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.RecordPageDto;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.CreatedBy;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.RelationType;
import com.example.contor.domain.ReportScope.CollectionScope;
import com.example.contor.domain.Slug;
import com.example.contor.fieldtype.TestFieldTypes;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationTypeRepository;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Aggregate;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Bucket;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.GroupBy;
import com.example.contor.persistence.dynamic.DynamicAggregateRepository.Measure;
import com.example.contor.persistence.dynamic.DynamicRecordRepository;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollectionAnalysisServiceTest {

    private static final Condition FILTERED = DSL.condition("filtered");

    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private RelationTypeRepository relationTypeRepository;
    @Mock
    private RecordService recordService;
    @Mock
    private DynamicAggregateRepository aggregateRepository;
    @Mock
    private DynamicRecordRepository dynamicRecordRepository;

    private CollectionAnalysisService service;
    private Collection customers;
    private Collection orders;
    private Collection shipments;

    @BeforeEach
    void setUp() {
        service = new CollectionAnalysisService(collectionRepository, relationTypeRepository, recordService, aggregateRepository,
                dynamicRecordRepository, TestFieldTypes.registry());
        customers = collection(4L, "customers", "Customers");
        field(customers, "name", "Name", FieldType.TEXT, Map.of());
        orders = collection(5L, "sales_orders", "Sales orders");
        field(orders, "customer", "Customer", FieldType.TEXT, Map.of());
        field(orders, "customer_ref", "Customer record", FieldType.RELATION, Map.of("targetCollectionId", 4));
        field(orders, "total", "Total", FieldType.MONEY, Map.of());
        field(orders, "state", "State", FieldType.STATUS, Map.of("states", List.of("received", "shipped", "delivered")));
        field(orders, "requested", "Requested delivery", FieldType.DATE, Map.of());
        shipments = collection(6L, "shipments", "Shipments");
        when(collectionRepository.findByArchivedAtIsNullOrderByNameAsc()).thenReturn(List.of(customers, orders, shipments));
        when(recordService.condition(any(), any(), any())).thenReturn(DSL.noCondition());
        when(dynamicRecordRepository.count(anyString(), any(Condition.class))).thenReturn(6L);
    }

    @Test
    void run_GroupsByATextFieldAndRanksBySumInTheMainCurrency() {
        when(aggregateRepository.aggregate(eq("c_sales_orders"), any(), any())).thenReturn(List.of(
                new Bucket("kessler gmbh", "Kessler GmbH", "EUR", 2, 2, new BigDecimal("150.5"), List.of(1L, 2L)),
                new Bucket("moreau sarl", "Moreau SARL", "CHF", 1, 1, new BigDecimal("80"), List.of(3L)),
                new Bucket("moreau sarl", "Moreau SARL", null, 1, 0, null, List.of(4L)),
                new Bucket("dijkstra", "Dijkstra", "EUR", 1, 1, new BigDecimal("200.0000"), List.of(5L)),
                new Bucket(null, null, null, 1, 0, null, List.of(6L))));
        when(recordService.findByIds(eq(5L), anyList())).thenReturn(List.of(
                order(1L, "SO-1", "100", "EUR"), order(2L, "SO-2", "50.5", "EUR"), order(5L, "SO-5", "200", "EUR")));

        List<AnalysisInput> analyses = service.run(new CollectionPlan(List.of(
                new CollectionPlan.Analysis("Top customers", "sales_orders", "customer", "sum:total", false, 2, List.of()))), 5L, null);

        ArgumentCaptor<Aggregate> aggregate = ArgumentCaptor.forClass(Aggregate.class);
        verify(aggregateRepository).aggregate(eq("c_sales_orders"), any(), aggregate.capture());
        assertThat(aggregate.getValue().groupBy()).isInstanceOfSatisfying(GroupBy.ByColumn.class, group -> {
            assertThat(group.column().getName()).isEqualTo("customer");
            assertThat(group.text()).isTrue();
        });
        assertThat(aggregate.getValue().measure()).isEqualTo(Measure.SUM);
        assertThat(aggregate.getValue().value().getName()).isEqualTo("total_amount");
        assertThat(aggregate.getValue().currency().getName()).isEqualTo("total_currency");

        AnalysisInput analysis = analyses.getFirst();
        assertThat(analysis.title()).isEqualTo("Top customers");
        assertThat(analysis.columns()).containsExactly("#", "Customer", "Sales orders", "Sum of Total");
        assertThat(analysis.measureColumn()).isEqualTo(3);
        assertThat(analysis.rows()).extracting(AnalysisRow::cells).containsExactly(
                List.of("1", "Dijkstra", "1", "200 EUR"),
                List.of("2", "Kessler GmbH", "2", "150.5 EUR"));
        assertThat(analysis.description()).contains("6 Sales orders", "grouped by Customer", "largest first", "top 2 of 3 groups");
        assertThat(analysis.note()).contains("1 of the records has no Customer", "not converted", "EUR");
        assertThat(analysis.rows().get(1).sources()).extracting(RowSource::recordId, RowSource::fieldSlug, RowSource::recordTitle)
                .containsExactly(tuple(1L, "total", "SO-1"), tuple(2L, "total", "SO-2"));
        assertThat(analysis.rows().get(1).sources().getFirst().value()).isEqualTo(Map.of("amount", new BigDecimal("100"), "currency", "EUR"));
    }

    @Test
    void run_CountsPerStatusSmallestFirstAndCitesTheRecords() {
        when(aggregateRepository.aggregate(any(), any(), any())).thenReturn(List.of(
                new Bucket("shipped", "shipped", null, 3, 3, null, List.of(1L, 2L, 3L)),
                new Bucket("delivered", "delivered", null, 1, 1, null, List.of(4L))));
        when(recordService.findByIds(eq(5L), anyList())).thenReturn(List.of(order(4L, "SO-4", null, null)));

        AnalysisInput analysis = service.run(new CollectionPlan(List.of(
                new CollectionPlan.Analysis("", "sales_orders", "state", "count", true, 10, List.of()))), 5L, null).getFirst();

        assertThat(analysis.title()).isEqualTo("Sales orders by State");
        assertThat(analysis.columns()).containsExactly("#", "State", "Sales orders");
        assertThat(analysis.measureColumn()).isEqualTo(2);
        assertThat(analysis.rows()).extracting(AnalysisRow::cells).containsExactly(List.of("1", "delivered", "1"), List.of("2", "shipped", "3"));
        assertThat(analysis.rows().getFirst().sources()).singleElement().satisfies(source -> {
            assertThat(source.fieldSlug()).isNull();
            assertThat(source.recordTitle()).isEqualTo("SO-4");
            assertThat(source.collectionName()).isEqualTo("Sales orders");
        });
    }

    @Test
    void run_GroupsByAReferenceFieldWithTheTargetRecordsTitles() {
        when(aggregateRepository.aggregate(any(), any(), any())).thenReturn(List.of(
                new Bucket(41L, null, null, 2, 2, null, List.of(1L, 2L))));
        when(recordService.findByIds(4L, List.of(41L))).thenReturn(List.of(record(41L, 4L, "Kessler GmbH", Map.of())));

        AnalysisInput analysis = service.run(plan("sales_orders", "customer_ref", "count"), 5L, null).getFirst();

        assertThat(analysis.rows()).extracting(AnalysisRow::cells).containsExactly(List.of("1", "Kessler GmbH", "2"));
    }

    @Test
    void run_GroupsByTheRecordsAtTheOtherEndOfARelationType() {
        RelationType ships = RelationType.builder().id(new RelationType.RelationTypeId(9L)).slug(new Slug("ships")).name("ships")
                .fromCollection(shipments).toCollection(orders).createdBy(CreatedBy.USER).build();
        when(relationTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ships));
        when(aggregateRepository.aggregate(any(), any(), any())).thenReturn(List.of(new Bucket(70L, null, null, 2, 2, null, List.of(1L, 2L))));
        when(recordService.findByIds(6L, List.of(70L))).thenReturn(List.of(record(70L, 6L, "DHL 123", Map.of())));

        AnalysisInput analysis = service.run(plan("sales_orders", "link:ships", "count"), 5L, null).getFirst();

        ArgumentCaptor<Aggregate> aggregate = ArgumentCaptor.forClass(Aggregate.class);
        verify(aggregateRepository).aggregate(any(), any(), aggregate.capture());
        assertThat(aggregate.getValue().groupBy()).isEqualTo(new GroupBy.ByLink(9L, 5L, 6L, false));
        assertThat(analysis.columns()).containsExactly("#", "Shipments", "Sales orders");
        assertThat(analysis.rows()).extracting(AnalysisRow::cells).containsExactly(List.of("1", "DHL 123", "2"));
    }

    @Test
    void run_EachRecordIsRankedByItsOwnValue() {
        when(recordService.listRecords(eq(5L), anyList(), isNull(), eq("-total"), eq(0), eq(2)))
                .thenReturn(new RecordPageDto(new ArrayList<>(List.of(order(5L, "SO-5", "200", "EUR"), order(1L, "SO-1", "100", "EUR"))), 0, 2, 4L, 2));

        AnalysisInput analysis = service.run(new CollectionPlan(List.of(
                new CollectionPlan.Analysis("Largest orders", "sales_orders", "each", "max:total", false, 2, List.of()))), 5L, null).getFirst();

        ArgumentCaptor<List<String>> filters = ArgumentCaptor.captor();
        verify(recordService).listRecords(eq(5L), filters.capture(), isNull(), eq("-total"), eq(0), eq(2));
        assertThat(filters.getValue()).containsExactly("total:notempty");
        assertThat(analysis.columns()).containsExactly("#", "Sales orders", "Total");
        assertThat(analysis.rows()).extracting(AnalysisRow::cells).containsExactly(List.of("1", "SO-5", "200 EUR"), List.of("2", "SO-1", "100 EUR"));
        assertThat(analysis.rows().getFirst().sources()).singleElement().satisfies(source -> assertThat(source.fieldSlug()).isEqualTo("total"));
        assertThat(analysis.description()).contains("top 2 of 4");
    }

    @Test
    void run_AllRecordsTogetherIsOneRow() {
        when(aggregateRepository.aggregate(any(), any(), any())).thenReturn(List.of(
                new Bucket(null, null, "EUR", 3, 3, new BigDecimal("116.8333333"), List.of(1L, 2L, 5L))));

        AnalysisInput analysis = service.run(plan("sales_orders", "", "avg:total"), 5L, null).getFirst();

        assertThat(analysis.columns()).containsExactly("#", "All", "Sales orders", "Average of Total");
        assertThat(analysis.rows()).extracting(AnalysisRow::cells).containsExactly(List.of("1", "All matching records", "3", "116.83 EUR"));
    }

    @Test
    void run_DropsWhatTheModelInventedAndKeepsTheRest() {
        when(recordService.condition(5L, List.of("bogus:eq:1"), null)).thenThrow(new IllegalArgumentException("Invalid filter"));
        when(recordService.condition(5L, List.of("state:eq:shipped"), null)).thenReturn(FILTERED);
        when(aggregateRepository.aggregate(any(), any(), any())).thenReturn(List.of());

        List<AnalysisInput> analyses = service.run(new CollectionPlan(List.of(
                new CollectionPlan.Analysis("", "invoices", "state", "count", false, 5, List.of()),
                new CollectionPlan.Analysis("", "sales_orders", "requested", "count", false, 5, List.of()),
                new CollectionPlan.Analysis("", "sales_orders", "state", "sum:state", false, 5, List.of("bogus:eq:1", "state:eq:shipped")))),
                5L, null);

        assertThat(analyses).singleElement().satisfies(analysis -> assertThat(analysis.columns()).containsExactly("#", "State", "Sales orders"));
        ArgumentCaptor<Aggregate> aggregate = ArgumentCaptor.forClass(Aggregate.class);
        verify(aggregateRepository).aggregate(eq("c_sales_orders"), eq(FILTERED), aggregate.capture());
        assertThat(aggregate.getValue().measure()).as("a measure of a field that is no number is a count").isEqualTo(Measure.COUNT);
    }

    @Test
    void run_TheTableFiltersApplyToTheAnchorCollectionOnly() {
        CollectionScope scope = new CollectionScope(List.of("state:eq:shipped"), "acme");
        when(recordService.condition(5L, List.of("state:eq:shipped"), "acme")).thenReturn(FILTERED);
        when(aggregateRepository.aggregate(any(), any(), any())).thenReturn(List.of());

        service.run(new CollectionPlan(List.of(
                new CollectionPlan.Analysis("", "sales_orders", "state", "count", false, 5, List.of()),
                new CollectionPlan.Analysis("", "customers", "name", "count", false, 5, List.of()))), 5L, scope);

        verify(aggregateRepository).aggregate(eq("c_sales_orders"), eq(FILTERED), any());
        verify(recordService).condition(4L, List.of(), null);
    }

    @Test
    void catalog_DescribesCollectionsFieldsAndRelationTypes() {
        RelationType ships = RelationType.builder().id(new RelationType.RelationTypeId(9L)).slug(new Slug("ships")).name("ships")
                .fromCollection(shipments).toCollection(orders).createdBy(CreatedBy.USER).build();
        when(relationTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ships));

        String catalog = service.catalog(5L);

        assertThat(catalog).contains("- sales_orders: Sales orders, 6 records (the collection the user is looking at)",
                "customer (text, Customer)", "total (money, Total)", "state (status: received, shipped, delivered)",
                "customer_ref (reference to customers, Customer record)", "- ships: Shipments ships Sales orders");
        assertThat(service.collectionSlugs()).containsExactly("customers", "sales_orders", "shipments");
    }

    @Test
    void overview_CountsTheRecordsTheirValuesAndSums() {
        when(recordService.condition(5L, List.of("state:ne:received"), null)).thenReturn(FILTERED);
        when(dynamicRecordRepository.count("c_sales_orders", FILTERED)).thenReturn(4L);
        when(aggregateRepository.aggregate(eq("c_sales_orders"), eq(FILTERED), any())).thenAnswer(invocation -> {
            Aggregate aggregate = invocation.getArgument(2);
            if (aggregate.groupBy() instanceof GroupBy.ByColumn) {
                return List.of(new Bucket("shipped", "shipped", null, 3, 3, null, List.of(1L, 2L, 3L)),
                        new Bucket(null, null, null, 1, 1, null, List.of(4L)));
            }
            return List.of(new Bucket(null, null, "EUR", 2, 2, new BigDecimal("300"), List.of(1L, 2L)),
                    new Bucket(null, null, null, 2, 0, null, List.of(3L, 4L)));
        });

        String overview = service.overview(5L, new CollectionScope(List.of("state:ne:received"), null));

        assertThat(overview).contains("Sales orders: 4 records (filters: state:ne:received)", "State: shipped 3, no value 1",
                "Total: sum 300 EUR (2 records), 2 records without a value");
        verify(aggregateRepository, never()).aggregate(any(), eq(DSL.noCondition()), any());
    }

    private static CollectionPlan plan(String collection, String groupBy, String measure) {
        return new CollectionPlan(List.of(new CollectionPlan.Analysis("", collection, groupBy, measure, false, 10, List.of())));
    }

    private static Collection collection(Long id, String slug, String name) {
        return Collection.builder().id(new Collection.CollectionId(id)).slug(new Slug(slug)).name(name).tableName("c_" + slug)
                .createdBy(CreatedBy.USER).fields(new ArrayList<>()).build();
    }

    private static void field(Collection collection, String slug, String name, FieldType type, Map<String, Object> options) {
        collection.getFields().add(CollectionField.builder().id(new CollectionField.CollectionFieldId((long) collection.getFields().size() + 1))
                .collection(collection).slug(new Slug(slug)).name(name).type(type).options(new HashMap<>(options))
                .position(collection.getFields().size()).columnName(slug).build());
    }

    private static RecordDto order(Long id, String title, String amount, String currency) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (amount != null) {
            values.put("total", Map.of("amount", new BigDecimal(amount), "currency", currency));
        }
        return record(id, 5L, title, values);
    }

    private static RecordDto record(Long id, Long collectionId, String title, Map<String, Object> values) {
        return new RecordDto(id, collectionId, new LinkedHashMap<>(values), new HashMap<>(),
                OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), "USER").title(title);
    }
}
