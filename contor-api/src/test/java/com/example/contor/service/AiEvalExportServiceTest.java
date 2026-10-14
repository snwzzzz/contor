package com.example.contor.service;

import com.example.contor.api.model.EvalCaseDto;
import com.example.contor.api.model.EvalCaseExportDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.Proposal;
import com.example.contor.domain.ProposalKind;
import com.example.contor.domain.ProposalStatus;
import com.example.contor.domain.Slug;
import com.example.contor.domain.UserRole;
import com.example.contor.persistence.CollectionRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.ProposalRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEvalExportServiceTest {

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @InjectMocks
    private AiEvalExportService service;

    @BeforeEach
    void login() {
        var principal = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        lenient().when(collectionRepository.findAll()).thenReturn(List.of(
                Collection.builder().id(new Collection.CollectionId(10L)).slug(new Slug("tasks")).build(),
                Collection.builder().id(new Collection.CollectionId(11L)).slug(new Slug("people")).build()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static EmailMessage email(long id, long ownerId) {
        MailAccount account = MailAccount.builder().id(new MailAccount.MailAccountId(2L))
                .owner(AppUser.builder().id(new AppUser.AppUserId(ownerId)).build()).emailAddress(new Email("me@example.com")).build();
        return EmailMessage.builder().id(new EmailMessage.EmailMessageId(id)).account(account).fromName("Anna Berger")
                .fromAddress("anna@acme.com").toRecipients(List.of(new EmailRecipient("Me", "me@example.com")))
                .subject("Interview").bodyText("See you Friday").receivedAt(Instant.parse("2026-10-06T08:00:00Z")).build();
    }

    private static Proposal proposal(ProposalKind kind, ProposalStatus status, Map<String, Object> payload, Map<String, Object> finalPayload) {
        return Proposal.builder().kind(kind).status(status).payload(payload).finalPayload(finalPayload).build();
    }

    private void stub(EmailMessage email, Proposal... proposals) {
        when(emailMessageRepository.findAllById(List.of(email.getId()))).thenReturn(List.of(email));
        List.of(proposals).forEach(proposal -> proposal.setSourceEmail(email));
        when(proposalRepository.findBySourceEmail_IdIn(List.of(email.getId()))).thenReturn(List.of(proposals));
    }

    @Test
    void exportCases_EmailIsExportedAsItIsSeenByTheModel() {
        EmailMessage email = email(5L, 1L);
        stub(email);

        EvalCaseExportDto export = service.exportCases(List.of(5L));

        assertThat(export.getCases()).hasSize(1);
        EvalCaseDto exported = export.getCases().get(0);
        assertThat(exported.getId()).isEqualTo("email-5");
        assertThat(exported.getEmail().getMailbox()).isEqualTo("me@example.com");
        assertThat(exported.getEmail().getFromAddress()).isEqualTo("anna@acme.com");
        assertThat(exported.getEmail().getTo()).containsExactly("me@example.com");
        assertThat(exported.getEmail().getBody()).isEqualTo("See you Friday");
        assertThat(exported.getEmail().getReceivedAt().toInstant()).isEqualTo(Instant.parse("2026-10-06T08:00:00Z"));
        assertThat(exported.getExpected().getRecords()).isEmpty();
        assertThat(exported.getMustNotProduce()).isEmpty();
    }

    @Test
    void exportCases_AppliedRecordProposalsBecomeExpectedRecordsUsingTheFinalPayload() {
        EmailMessage email = email(5L, 1L);
        stub(email,
                proposal(ProposalKind.CREATE_RECORD, ProposalStatus.EDITED_AND_APPROVED,
                        Map.of("collectionId", 10, "title", "Send CV", "values", Map.of("due_date", "2026-10-07")),
                        Map.of("collectionId", 10, "title", "Send CV to Acme", "values", Map.of("due_date", "2026-10-08"))),
                proposal(ProposalKind.CREATE_RECORD, ProposalStatus.AUTO_APPLIED,
                        Map.of("collectionId", 11, "title", "Anna Berger", "values", Map.of("email", "anna@acme.com")), null));

        EvalCaseDto exported = service.exportCases(List.of(5L)).getCases().get(0);

        assertThat(exported.getExpected().getRecords()).containsOnlyKeys("tasks", "people");
        assertThat(exported.getExpected().getRecords().get("tasks")).singleElement().satisfies(record -> {
            assertThat(record.getTitle()).isEqualTo("Send CV to Acme");
            assertThat(record.getValues()).containsEntry("due_date", "2026-10-08");
        });
        assertThat(exported.getExpected().getRecords().get("people").get(0).getValues()).containsEntry("email", "anna@acme.com");
    }

    @Test
    void exportCases_RejectedRecordProposalsBecomeMustNotProduce() {
        EmailMessage email = email(5L, 1L);
        stub(email, proposal(ProposalKind.CREATE_RECORD, ProposalStatus.REJECTED,
                Map.of("collectionId", 11, "title", "Newsletter Team", "values", Map.of()), null));

        EvalCaseDto exported = service.exportCases(List.of(5L)).getCases().get(0);

        assertThat(exported.getMustNotProduce()).singleElement().satisfies(forbidden -> {
            assertThat(forbidden.getCollection()).isEqualTo("people");
            assertThat(forbidden.getTitle()).isEqualTo("Newsletter Team");
        });
        assertThat(exported.getExpected().getRecords()).isEmpty();
    }

    @Test
    void exportCases_PendingFailedAndOtherKindsAreIgnored() {
        EmailMessage email = email(5L, 1L);
        stub(email,
                proposal(ProposalKind.CREATE_RECORD, ProposalStatus.PENDING, Map.of("collectionId", 10, "title", "Pending", "values", Map.of()), null),
                proposal(ProposalKind.CREATE_RECORD, ProposalStatus.FAILED, Map.of("collectionId", 10, "title", "Failed", "values", Map.of()), null),
                proposal(ProposalKind.SET_LABEL, ProposalStatus.APPROVED, Map.of("labelId", 3), null),
                proposal(ProposalKind.CREATE_RECORD, ProposalStatus.APPROVED, Map.of("collectionId", 999, "title", "Unknown collection", "values", Map.of()), null));

        EvalCaseDto exported = service.exportCases(List.of(5L)).getCases().get(0);

        assertThat(exported.getExpected().getRecords()).isEmpty();
        assertThat(exported.getMustNotProduce()).isEmpty();
    }

    @Test
    void exportCases_EmailsOfOtherUsersAndUnknownIdsAreSkipped() {
        EmailMessage foreign = email(5L, 2L);
        when(emailMessageRepository.findAllById(List.of(foreign.getId(), new EmailMessage.EmailMessageId(6L)))).thenReturn(List.of(foreign));

        assertThat(service.exportCases(List.of(5L, 6L)).getCases()).isEmpty();
    }
}
