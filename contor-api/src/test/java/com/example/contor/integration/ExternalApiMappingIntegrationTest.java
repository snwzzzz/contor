package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.api.model.DecideProposalCommand;
import com.example.contor.api.model.ProposalDecision;
import com.example.contor.domain.ApiFieldMapping;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.Email;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.Slug;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.security.OutboundRequestGuard;
import com.example.contor.service.AiModelService;
import com.example.contor.service.ApiMappingModelService;
import com.example.contor.service.ExternalApiPollScheduler;
import com.example.contor.service.ProposalService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Learn once, then map in code, end to end against real JPA and Postgres: a poll finds items of an endpoint without a
 * mapping, the assistant (stubbed) proposes one, approving it turns the items into record proposals, approving one of
 * those creates the record linked to its item, and a later change of the item proposes an update. The guard and the model
 * are the stubs. Not rolled back (the steps run on their own threads); the app, its proposals and records are removed after.
 */
// The test runs the poll itself: no scheduled tick may poll its due app at the same time.
@SpringBootTest(properties = "app.external-apis.enabled=false")
@Import(PostgresTestcontainersConfiguration.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExternalApiMappingIntegrationTest {

    @Autowired
    private ExternalApiPollScheduler scheduler;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ExternalApiRepository externalApiRepository;

    @Autowired
    private ExternalApiEndpointRepository externalApiEndpointRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboundRequestGuard guard;

    @MockitoBean
    private ApiMappingModelService apiMappingModelService;

    @MockitoBean
    private AiModelService aiModelService;

    private AppUser owner;
    private ExternalApi crm;
    private ExternalApiEndpoint contacts;
    private final List<Long> proposals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        owner = appUserRepository.saveAndFlush(AppUser.builder().username(new Username("mapping-owner")).email(new Email("mapping-owner@example.com"))
                .passwordHash("$argon2id$test").role(UserRole.OWNER).build());
        var principal = AuthenticatedUser.of(owner);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        crm = externalApiRepository.saveAndFlush(ExternalApi.builder().owner(owner).name("CRM").slug(new Slug("crm"))
                .baseUrl(new BaseUrl("https://api.crm.example")).nextCallAt(Instant.now().minusSeconds(60)).build());
        contacts = externalApiEndpointRepository.saveAndFlush(ExternalApiEndpoint.builder().api(crm).name("Contacts")
                .path(new ApiPath("/contacts")).itemsPath(new JsonPathExpr("$.data[*]")).itemKeyPath(new JsonPathExpr("$.id")).build());
        Long people = collectionRepository.findBySlug(new Slug("people")).orElseThrow().getId().id();
        when(aiModelService.isReady()).thenReturn(true);
        when(apiMappingModelService.propose(anyString(), anyString(), any(), anyList(), anyList())).thenReturn(Optional.of(
                new ApiMappingModelService.MappingDraft(people, new JsonPathExpr("$.name"),
                        List.of(new ApiFieldMapping("full_name", "$.name"), new ApiFieldMapping("email", "$.mail")), new BigDecimal("0.9"),
                        "CRM contacts are people")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        proposals.addAll(jdbcTemplate.queryForList("select id from proposal where payload ->> 'endpointId' = ?", Long.class,
                String.valueOf(contacts.getId().id())));
        if (!proposals.isEmpty()) {
            jdbcTemplate.update("delete from proposal where id in (" + String.join(",", proposals.stream().map(String::valueOf).toList()) + ")");
        }
        jdbcTemplate.update("delete from c_people where email like '%@crm-contacts.example'");
        // The app's endpoints, items and their record sources go with it; the owner stays (the audit log refers to them).
        jdbcTemplate.update("delete from external_api where id = ?", crm.getId().id());
    }

    private void answer(String body) {
        when(guard.send(any())).thenReturn(new OutboundRequestGuard.Response(200, Map.of("content-type", "application/json"),
                body.getBytes(StandardCharsets.UTF_8), false));
    }

    private void makeDue() {
        jdbcTemplate.update("update external_api set next_call_at = ? where id = ?", Timestamp.from(Instant.now().minusSeconds(60)), crm.getId().id());
    }

    /** Waits for work on other threads (the AI thread, the ingest after a commit). */
    private static <T> T eventually(Supplier<T> value, Predicate<T> done) throws InterruptedException {
        T current = value.get();
        for (int i = 0; i < 100 && !done.test(current); i++) {
            Thread.sleep(100);
            current = value.get();
        }
        return current;
    }

    private List<Map<String, Object>> recordProposals(String kind) {
        return jdbcTemplate.queryForList("select p.id, p.status, p.payload ->> 'title' as title, p.payload -> 'values' ->> 'email' as email "
                + "from proposal p join external_api_item i on i.id = p.source_api_item_id where i.endpoint_id = ? and p.kind = ? order by p.id",
                contacts.getId().id(), kind);
    }

    @Test
    void items_become_records_once_the_proposed_mapping_is_approved() throws InterruptedException {
        answer("{\"data\":[{\"id\":1,\"name\":\"Jane Doe\",\"mail\":\"jane@crm-contacts.example\"},"
                + "{\"id\":2,\"name\":\"Max Muster\",\"mail\":\"max@crm-contacts.example\"}]}");
        scheduler.pollDue();

        Long mappingProposal = eventually(() -> jdbcTemplate.queryForList("select id from proposal where kind = 'MAP_API_ENDPOINT' "
                + "and payload ->> 'endpointId' = ?", Long.class, String.valueOf(contacts.getId().id())), ids -> !ids.isEmpty()).getFirst();
        assertThat(jdbcTemplate.queryForObject("select state from external_api_mapping where endpoint_id = ?", String.class, contacts.getId().id()))
                .isEqualTo("PROPOSED");
        assertThat(recordProposals("CREATE_RECORD")).as("nothing before the mapping is approved").isEmpty();

        proposalService.decideProposal(mappingProposal, new DecideProposalCommand(ProposalDecision.APPROVE));

        assertThat(jdbcTemplate.queryForObject("select state from external_api_mapping where endpoint_id = ?", String.class, contacts.getId().id()))
                .isEqualTo("ACTIVE");
        var created = eventually(() -> recordProposals("CREATE_RECORD"), rows -> rows.size() == 2);
        assertThat(created).extracting(row -> row.get("title")).containsExactly("Jane Doe", "Max Muster");
        assertThat(created).extracting(row -> row.get("status")).containsOnly("PENDING");

        Long jane = (Long) created.getFirst().get("id");
        proposalService.decideProposal(jane, new DecideProposalCommand(ProposalDecision.APPROVE));
        var record = jdbcTemplate.queryForMap("select id, full_name, created_by from c_people where email = 'jane@crm-contacts.example'");
        assertThat(record).containsEntry("full_name", "Jane Doe").containsEntry("created_by", "AI");
        assertThat(jdbcTemplate.queryForObject("select count(*) from record_source s join external_api_item i on i.id = s.api_item_id "
                + "where i.endpoint_id = ? and s.record_id = ? and s.role = 'CREATED_FROM' and s.email_id is null", Long.class,
                contacts.getId().id(), record.get("id"))).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select record_id from external_api_item where endpoint_id = ? and item_key = '1'", Long.class,
                contacts.getId().id())).isEqualTo(record.get("id"));

        makeDue();
        answer("{\"data\":[{\"id\":1,\"name\":\"Jane Doe\",\"mail\":\"jane.doe@crm-contacts.example\"},"
                + "{\"id\":2,\"name\":\"Max Muster\",\"mail\":\"max.m@crm-contacts.example\"}]}");
        scheduler.pollDue();

        assertThat(recordProposals("UPDATE_RECORD")).singleElement().satisfies(update -> {
            assertThat(update.get("status")).isEqualTo("PENDING");
            assertThat(update.get("email")).isEqualTo("jane.doe@crm-contacts.example");
        });
        assertThat(recordProposals("CREATE_RECORD")).as("the pending one follows the item instead of a second one")
                .filteredOn(row -> "PENDING".equals(row.get("status"))).singleElement()
                .satisfies(pending -> assertThat(pending.get("email")).isEqualTo("max.m@crm-contacts.example"));
    }
}
