package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.api.model.DecideProposalCommand;
import com.example.contor.api.model.NewRecordCommand;
import com.example.contor.api.model.NewRelationCommand;
import com.example.contor.api.model.ProposalDecision;
import com.example.contor.api.model.ProposalState;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.domain.Slug;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.RecordValidationException;
import com.example.contor.exception.ResourceInUseException;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.persistence.RelationTypeRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.service.ProposalService;
import com.example.contor.service.RecordService;
import com.example.contor.service.RelationService;
import com.example.contor.service.RelationTypeService;
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

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proposals applied to the real collection engine, and relations between real records. Rolled back per test. */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProposalAndRelationIntegrationTest {

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private RecordService recordService;

    @Autowired
    private RelationService relationService;

    @Autowired
    private RelationTypeService relationTypeService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private RelationTypeRepository relationTypeRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MailAccountRepository mailAccountRepository;

    @Autowired
    private EmailMessageRepository emailMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AppUser owner;
    private EmailMessage email;

    @BeforeEach
    void setup() {
        owner = appUserRepository.save(AppUser.builder().username(new Username("graph_owner")).email(new Email("owner@example.com"))
                .passwordHash("x").role(UserRole.OWNER).build());
        var principal = AuthenticatedUser.of(owner);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        var account = mailAccountRepository.save(MailAccount.builder().owner(owner).provider(MailProvider.MICROSOFT_GRAPH)
                .emailAddress(new Email("me@example.com")).build());
        email = emailMessageRepository.save(EmailMessage.builder().account(account).providerMessageId("AAMk-1")
                .subject("Meet Jane").receivedAt(Instant.parse("2026-01-01T10:00:00Z")).build());
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private Long collection(String slug) {
        return collectionRepository.findBySlug(new Slug(slug)).orElseThrow().getId().id();
    }

    private Proposal proposal(ProposalKind kind, Map<String, Object> payload) {
        return proposalRepository.saveAndFlush(Proposal.builder().kind(kind).payload(payload).sourceEmail(email).model("test").build());
    }

    private Long person(String name) {
        return recordService.createRecord(collection("people"), new NewRecordCommand().values(Map.of("full_name", name))).getId();
    }

    @Test
    void approving_a_create_record_proposal_creates_the_record_as_ai_and_links_the_email() {
        var proposal = proposal(ProposalKind.CREATE_RECORD, Map.of("collectionId", collection("people"),
                "title", "Jane", "values", Map.of("full_name", "Jane Doe", "email", "jane@example.com")));

        var decided = proposalService.decideProposal(proposal.getId().id(), new DecideProposalCommand(ProposalDecision.APPROVE));

        assertThat(decided.getStatus()).isEqualTo(ProposalState.APPROVED);
        assertThat(decided.getDecidedById()).isEqualTo(owner.getId().id());
        var record = jdbcTemplate.queryForMap("select id, full_name, created_by from c_people where email = 'jane@example.com'");
        assertThat(record).containsEntry("full_name", "Jane Doe").containsEntry("created_by", "AI");
        assertThat(jdbcTemplate.queryForObject(
                "select role from record_source where collection_id = ? and record_id = ? and email_id = ?", String.class,
                collection("people"), record.get("id"), email.getId().id())).isEqualTo("CREATED_FROM");
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where action = 'PROPOSAL_APPROVED' and entity_id = ?",
                Long.class, proposal.getId().id())).isEqualTo(1);
    }

    @Test
    void approving_with_an_edited_payload_applies_the_edit() {
        Long jane = person("Jane Doe");
        var proposal = proposal(ProposalKind.UPDATE_RECORD, Map.of("collectionId", collection("people"), "recordId", jane,
                "values", Map.of("role_title", "Engineer")));

        var decided = proposalService.decideProposal(proposal.getId().id(), new DecideProposalCommand(ProposalDecision.APPROVE)
                .payload(Map.of("collectionId", collection("people"), "recordId", jane, "values", Map.of("role_title", "CTO"))));

        assertThat(decided.getStatus()).isEqualTo(ProposalState.EDITED_AND_APPROVED);
        assertThat(recordService.getRecord(collection("people"), jane).getValues()).containsEntry("role_title", "CTO");
        assertThat(jdbcTemplate.queryForObject("select role from record_source where record_id = ?", String.class, jane))
                .isEqualTo("UPDATED_FROM");
    }

    @Test
    void an_invalid_proposal_is_reported_and_creates_nothing() {
        var proposal = proposal(ProposalKind.CREATE_RECORD, Map.of("collectionId", collection("people"), "values", Map.of("email", "nope")));

        assertThatThrownBy(() -> proposalService.decideProposal(proposal.getId().id(), new DecideProposalCommand(ProposalDecision.APPROVE)))
                .isInstanceOf(RecordValidationException.class)
                .hasMessageContaining("full_name: is required")
                .hasMessageContaining("email: must be an email address");
        assertThat(jdbcTemplate.queryForObject("select count(*) from c_people", Long.class)).isZero();
    }

    @Test
    void rejecting_records_the_decision_only() {
        var proposal = proposal(ProposalKind.CREATE_RECORD, Map.of("collectionId", collection("people"), "values", Map.of("full_name", "Jane")));

        var decided = proposalService.decideProposal(proposal.getId().id(), new DecideProposalCommand(ProposalDecision.REJECT));

        assertThat(decided.getStatus()).isEqualTo(ProposalState.REJECTED);
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(jdbcTemplate.queryForObject("select count(*) from c_people", Long.class)).isZero();
        assertThat(proposalService.countProposals(ProposalState.REJECTED, null, null).getCount()).isEqualTo(1);
    }

    @Test
    void relates_records_according_to_the_relation_type() {
        Long jane = person("Jane");
        Long john = person("John");
        Long acme = recordService.createRecord(collection("organizations"), new NewRecordCommand().values(Map.of("name", "ACME"))).getId();
        Long worksAt = relationTypeRepository.findBySlug(new Slug("works_at")).orElseThrow().getId().id();
        Long knows = relationTypeRepository.findBySlug(new Slug("knows")).orElseThrow().getId().id();

        relationService.createRelation(new NewRelationCommand(worksAt, collection("people"), jane, collection("organizations"), acme)
                .attributes(Map.of("role", "CTO")));
        relationService.createRelation(new NewRelationCommand(knows, collection("people"), jane, collection("people"), john));

        assertThat(relationService.listRelations(collection("people"), jane)).hasSize(2);
        assertThat(relationService.listRelations(collection("organizations"), acme)).singleElement()
                .satisfies(relation -> assertThat(relation.getAttributes()).containsEntry("role", "CTO"));
        assertThatThrownBy(() -> relationService.createRelation(new NewRelationCommand(knows, collection("people"), john, collection("people"), jane)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> relationService.createRelation(new NewRelationCommand(worksAt, collection("organizations"), acme, collection("people"), jane)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> relationTypeService.deleteRelationType(worksAt)).isInstanceOf(ResourceInUseException.class);
    }
}
