package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.CollectionFieldType;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewCollectionFieldCommand;
import com.example.contor.api.model.NewRecordCommand;
import com.example.contor.api.model.RecordDto;
import com.example.contor.api.model.UpdateRecordCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.Slug;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.RecordValidationException;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.RelationTypeRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.service.CollectionSchemaService;
import com.example.contor.service.RecordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The collection engine against real Postgres: metadata + generated DDL + records. Each test runs in a transaction
 * that is rolled back afterwards, DDL included.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CollectionEngineIntegrationTest {

    @Autowired
    private CollectionSchemaService schemaService;

    @Autowired
    private RecordService recordService;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private RelationTypeRepository relationTypeRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AppUser owner;

    @BeforeEach
    void loginAsOwner() {
        owner = appUserRepository.save(AppUser.builder().username(new Username("engine_owner")).email(new Email("owner@example.com"))
                .passwordHash("x").role(UserRole.OWNER).build());
        var principal = AuthenticatedUser.of(owner);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static NewCollectionFieldCommand field(String slug, CollectionFieldType type) {
        return new NewCollectionFieldCommand(slug, slug, type);
    }

    private Long people() {
        return collectionRepository.findBySlug(new Slug("people")).orElseThrow().getId().id();
    }

    private Map<String, String> columnTypes(String table) {
        Map<String, String> types = new HashMap<>();
        jdbcTemplate.query("select column_name, data_type from information_schema.columns where table_name = ?",
                rs -> { types.put(rs.getString(1), rs.getString(2)); }, table);
        return types;
    }

    private CollectionDto deliveries() {
        return schemaService.createCollection(new NewCollectionCommand("deliveries", "Deliveries").fields(List.of(
                field("tracking", CollectionFieldType.TEXT).required(true).uniqueValue(true),
                field("details", CollectionFieldType.LONG_TEXT),
                field("weight", CollectionFieldType.NUMBER),
                field("price", CollectionFieldType.MONEY),
                field("eta", CollectionFieldType.DATE),
                field("shipped_at", CollectionFieldType.DATETIME),
                field("insured", CollectionFieldType.BOOLEAN),
                field("carrier", CollectionFieldType.ENUM).options(Map.of("values", List.of("DHL", "UPS"))),
                field("state", CollectionFieldType.STATUS).options(Map.of("states", List.of("ORDERED", "SHIPPED", "DELIVERED"),
                        "transitions", Map.of("ORDERED", List.of("SHIPPED"), "SHIPPED", List.of("DELIVERED")))),
                field("tracking_url", CollectionFieldType.URL),
                field("contact_email", CollectionFieldType.EMAIL),
                field("contact_phone", CollectionFieldType.PHONE),
                field("recipient", CollectionFieldType.RELATION).options(Map.of("targetCollectionId", people())),
                field("label", CollectionFieldType.FILE))));
    }

    private Long person(String name) {
        return recordService.createRecord(people(), new NewRecordCommand().title(name).values(Map.of("full_name", name))).getId();
    }

    @Test
    void seeds_people_and_organizations_with_relation_types() {
        var people = collectionRepository.findBySlug(new Slug("people")).orElseThrow();
        var organizations = collectionRepository.findBySlug(new Slug("organizations")).orElseThrow();

        assertThat(people.isSystem()).isTrue();
        assertThat(people.getFields()).extracting(f -> f.getSlug().value())
                .containsExactly("full_name", "email", "phone", "role_title", "notes");
        assertThat(organizations.getFields()).extracting(f -> f.getSlug().value())
                .containsExactly("name", "domain", "website", "notes");
        assertThat(columnTypes("c_people")).containsKeys("id", "title", "full_name", "email", "phone", "role_title", "notes");
        var worksAt = relationTypeRepository.findBySlug(new Slug("works_at")).orElseThrow();
        assertThat(worksAt.getInverseName()).isEqualTo("employs");
        assertThat(worksAt.isDirected()).isTrue();
        assertThat(worksAt.getFromCollection().getId()).isEqualTo(people.getId());
        assertThat(worksAt.getToCollection().getId()).isEqualTo(organizations.getId());
        assertThat(relationTypeRepository.findBySlug(new Slug("knows")).orElseThrow().isDirected()).isFalse();
    }

    @Test
    void creates_a_collection_with_every_field_type() {
        CollectionDto deliveries = deliveries();

        assertThat(deliveries.getFields()).hasSize(CollectionFieldType.values().length);
        assertThat(columnTypes("c_deliveries"))
                .containsEntry("tracking", "text")
                .containsEntry("weight", "numeric")
                .containsEntry("price_amount", "numeric")
                .containsEntry("price_currency", "character")
                .containsEntry("eta", "date")
                .containsEntry("shipped_at", "timestamp with time zone")
                .containsEntry("insured", "boolean")
                .containsEntry("recipient", "bigint");
        assertThat(jdbcTemplate.queryForObject("select has_table_privilege('app_readonly', 'c_deliveries', 'SELECT')", Boolean.class)).isTrue();
        var history = jdbcTemplate.queryForList(
                "select from_version, to_version, executed_by, ddl_executed from collection_schema_change where collection_id = ?",
                deliveries.getId());
        assertThat(history).singleElement().satisfies(row -> {
            assertThat(row.get("from_version")).isEqualTo(0);
            assertThat(row.get("to_version")).isEqualTo(1);
            assertThat(row.get("executed_by")).isEqualTo(owner.getId().id());
            assertThat((String) row.get("ddl_executed")).contains("create table \"c_deliveries\"").contains("\"recipient\"");
        });
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where action = 'COLLECTION_CREATED' and entity_id = ?",
                Long.class, deliveries.getId())).isEqualTo(1);
    }

    @Test
    void round_trips_values_of_every_field_type() {
        var deliveries = deliveries();
        Long jane = person("Jane");
        var values = new HashMap<String, Object>();
        values.put("tracking", "T-1");
        values.put("details", "Leave at door");
        values.put("weight", 2.5);
        values.put("price", Map.of("amount", 19.99, "currency", "EUR"));
        values.put("eta", "2026-03-01");
        values.put("shipped_at", "2026-02-28T10:15:00+02:00");
        values.put("insured", true);
        values.put("carrier", "DHL");
        values.put("tracking_url", "https://dhl.example/T-1");
        values.put("contact_email", "jane@example.com");
        values.put("contact_phone", "+49 30 1234");
        values.put("recipient", jane);
        values.put("label", "files/label.pdf");

        RecordDto created = recordService.createRecord(deliveries.getId(), new NewRecordCommand().title("Parcel").values(values)
                .extra(Map.of("source", "manual")));
        RecordDto loaded = recordService.getRecord(deliveries.getId(), created.getId());

        assertThat(loaded.getTitle()).isEqualTo("Parcel");
        assertThat(loaded.getCreatedBy()).isEqualTo("USER");
        assertThat(loaded.getExtra()).containsEntry("source", "manual");
        assertThat(loaded.getValues())
                .containsEntry("tracking", "T-1")
                .containsEntry("weight", new BigDecimal("2.5"))
                .containsEntry("price", Map.of("amount", new BigDecimal("19.99"), "currency", "EUR"))
                .containsEntry("eta", "2026-03-01")
                .containsEntry("shipped_at", "2026-02-28T08:15:00Z")
                .containsEntry("insured", true)
                .containsEntry("state", "ORDERED")
                .containsEntry("recipient", jane)
                .containsEntry("label", "files/label.pdf");
    }

    @Test
    void validates_records_against_the_schema() {
        var deliveries = deliveries();
        recordService.createRecord(deliveries.getId(), new NewRecordCommand().values(Map.of("tracking", "T-1")));

        assertThatThrownBy(() -> recordService.createRecord(deliveries.getId(), new NewRecordCommand()
                .values(Map.of("weight", "heavy", "recipient", 999_999, "carrier", "FedEx"))))
                .isInstanceOf(RecordValidationException.class)
                .hasMessageContaining("tracking: is required")
                .hasMessageContaining("weight: must be a number")
                .hasMessageContaining("carrier: must be one of [DHL, UPS]");
        assertThatThrownBy(() -> recordService.createRecord(deliveries.getId(), new NewRecordCommand()
                .values(Map.of("tracking", "T-2", "recipient", 999_999))))
                .isInstanceOf(RecordValidationException.class)
                .hasMessage("recipient: record 999999 does not exist in 'people'");
        assertThatThrownBy(() -> recordService.createRecord(deliveries.getId(), new NewRecordCommand().values(Map.of("tracking", "T-1"))))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void partial_updates_and_status_transitions() {
        var deliveries = deliveries();
        var created = recordService.createRecord(deliveries.getId(), new NewRecordCommand().title("Parcel")
                .values(Map.of("tracking", "T-1", "carrier", "DHL")));

        recordService.updateRecord(deliveries.getId(), created.getId(), new UpdateRecordCommand().values(Map.of("state", "SHIPPED")));
        var cleared = new HashMap<String, Object>();
        cleared.put("carrier", null);
        var updated = recordService.updateRecord(deliveries.getId(), created.getId(), new UpdateRecordCommand().values(cleared));

        assertThat(updated.getValues()).containsEntry("state", "SHIPPED").containsEntry("tracking", "T-1");
        assertThat(updated.getValues().get("carrier")).isNull();
        assertThat(updated.getTitle()).isEqualTo("Parcel");
        assertThat(updated.getLastModified()).isNotNull();
        assertThatThrownBy(() -> recordService.updateRecord(deliveries.getId(), created.getId(),
                new UpdateRecordCommand().values(Map.of("state", "ORDERED"))))
                .hasMessage("state: transition from 'SHIPPED' to 'ORDERED' is not allowed");
    }

    @Test
    void filters_sorts_and_pages() {
        var deliveries = deliveries();
        for (int day = 1; day <= 5; day++) {
            recordService.createRecord(deliveries.getId(), new NewRecordCommand().values(Map.of(
                    "tracking", "T-" + day, "eta", "2026-03-0" + day, "carrier", day % 2 == 0 ? "UPS" : "DHL",
                    "price", Map.of("amount", day * 10, "currency", "EUR"))));
        }

        var page = recordService.listRecords(deliveries.getId(), List.of("carrier:eq:DHL", "price:gte:20"), "-eta", 0, 1);
        var text = recordService.listRecords(deliveries.getId(), List.of("tracking:contains:t-4"), null, 0, 10);
        var empty = recordService.listRecords(deliveries.getId(), List.of("details:empty"), "eta", 1, 2);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getItems()).extracting(r -> r.getValues().get("eta")).containsExactly("2026-03-05");
        assertThat(text.getItems()).extracting(r -> r.getValues().get("tracking")).containsExactly("T-4");
        assertThat(empty.getItems()).extracting(r -> r.getValues().get("eta")).containsExactly("2026-03-03", "2026-03-04");
    }

    @Test
    void adding_and_archiving_fields_changes_the_schema_and_keeps_the_column() {
        var deliveries = deliveries();
        var record = recordService.createRecord(deliveries.getId(), new NewRecordCommand().values(Map.of("tracking", "T-1")));

        var withField = schemaService.addField(deliveries.getId(), field("customs_code", CollectionFieldType.TEXT));
        var customsId = withField.getFields().stream().filter(f -> f.getSlug().equals("customs_code")).findFirst().orElseThrow().getId();
        recordService.updateRecord(deliveries.getId(), record.getId(), new UpdateRecordCommand().values(Map.of("customs_code", "HS-1")));
        var archived = schemaService.archiveField(deliveries.getId(), customsId);

        assertThat(withField.getSchemaVersion()).isEqualTo(2);
        assertThat(archived.getSchemaVersion()).isEqualTo(3);
        assertThat(columnTypes("c_deliveries")).containsKey("customs_code");
        assertThat(recordService.getRecord(deliveries.getId(), record.getId()).getValues()).doesNotContainKey("customs_code");
        assertThatThrownBy(() -> recordService.updateRecord(deliveries.getId(), record.getId(),
                new UpdateRecordCommand().values(Map.of("customs_code", "HS-2"))))
                .hasMessage("customs_code: is not a field of this collection");
        assertThat(jdbcTemplate.queryForList("select to_version from collection_schema_change where collection_id = ? order by to_version",
                Integer.class, deliveries.getId())).containsExactly(1, 2, 3);
    }

    @Test
    void deletes_records_and_nulls_relations_to_them() {
        var deliveries = deliveries();
        Long jane = person("Jane");
        var parcel = recordService.createRecord(deliveries.getId(), new NewRecordCommand().values(Map.of("tracking", "T-1", "recipient", jane)));

        recordService.deleteRecord(people(), jane);

        assertThat(recordService.getRecord(deliveries.getId(), parcel.getId()).getValues().get("recipient")).isNull();
    }
}
