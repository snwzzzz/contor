package com.example.contor.service;

import com.example.contor.api.model.CollectionDto;
import com.example.contor.api.model.NewCollectionCommand;
import com.example.contor.api.model.NewRelationTypeCommand;
import com.example.contor.api.model.RelationTypeDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailAccountStatus;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.AiSettings;
import com.example.contor.persistence.AiSettingsRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.MailAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoMailboxServiceTest {

    private static final String INBOX = """
            {"id": "m-0001", "account": "orders", "folder": "inbox", "conversationId": "c-halvorsen", "cc": ["alex.morgan@nordhafen.example"],
             "storyline": "A6", "expected": {"records": {}}, "needsReply": true,
             "email": {"mailbox": "orders@nordhafen.example", "fromName": "Ingrid Solberg", "fromAddress": "ingrid.solberg@halvorsen-verksted.example",
                       "to": ["orders@nordhafen.example"], "subject": "Purchase order HV-6120", "receivedAt": "2026-07-06T11:40:00Z",
                       "body": "Dear Nordhafen team,\\n\\nplease find our purchase order HV-6120."}}
            """;
    private static final String SENT = """
            {"id": "m-0002", "account": "alex", "folder": "sent", "conversationId": "c-halvorsen", "cc": [],
             "email": {"mailbox": "alex.morgan@nordhafen.example", "fromName": "Alex Morgan", "fromAddress": "alex.morgan@nordhafen.example",
                       "to": ["ingrid.solberg@halvorsen-verksted.example"], "subject": "RE: Purchase order HV-6120",
                       "receivedAt": "2026-07-31T14:00:00Z", "body": "Dear Ingrid, confirmed as SO-26-0145."}}
            """;

    @Mock
    private MailAccountRepository mailAccountRepository;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private CollectionSchemaService collectionSchemaService;

    @Mock
    private RelationTypeService relationTypeService;

    @Mock
    private AiSettingsRepository aiSettingsRepository;

    @TempDir
    private Path mailbox;

    private final AppUser owner = AppUser.builder().id(new AppUser.AppUserId(7L)).build();

    @BeforeEach
    void writeMailbox() throws IOException {
        Files.writeString(mailbox.resolve("m-0001.json"), INBOX);
        Files.writeString(mailbox.resolve("m-0002.json"), SENT);
        Files.writeString(mailbox.resolve("README.txt"), "not a message");
    }

    private DemoMailboxService service(String now) {
        return new DemoMailboxService(mailAccountRepository, emailMessageRepository, collectionSchemaService, relationTypeService,
                aiSettingsRepository, Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private void emptySchema() {
        when(collectionSchemaService.findAll()).thenReturn(new ArrayList<>());
        when(collectionSchemaService.createCollection(any())).thenAnswer(inv -> {
            NewCollectionCommand command = inv.getArgument(0);
            return new CollectionDto().id((long) command.getSlug().hashCode()).slug(command.getSlug());
        });
        when(relationTypeService.findAll()).thenReturn(List.of());
        when(mailAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private List<EmailMessage> savedEmails() {
        ArgumentCaptor<List<EmailMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(emailMessageRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void importMailbox_CreatesDemoAccountsSchemaAndEmails() {
        emptySchema();

        int imported = service("2026-10-13T08:00:00Z").importMailbox(owner, mailbox, false);

        assertThat(imported).isEqualTo(2);
        ArgumentCaptor<MailAccount> accounts = ArgumentCaptor.forClass(MailAccount.class);
        verify(mailAccountRepository, times(2)).save(accounts.capture());
        assertThat(accounts.getAllValues()).allSatisfy(account -> {
            assertThat(account.getProvider()).isEqualTo(MailProvider.DEMO);
            assertThat(account.getStatus()).isEqualTo(MailAccountStatus.ACTIVE);
            assertThat(account.getOwner()).isSameAs(owner);
            assertThat(account.getAccessToken()).isNull();
        });
        assertThat(accounts.getAllValues()).extracting(account -> account.getEmailAddress().value())
                .containsExactlyInAnyOrder("orders@nordhafen.example", "alex.morgan@nordhafen.example");
        verify(collectionSchemaService, times(BusinessCollections.commands().size())).createCollection(any());
        verify(relationTypeService, times(BusinessCollections.relationTypes().size())).createRelationType(any(NewRelationTypeCommand.class));

        List<EmailMessage> emails = savedEmails();
        EmailMessage inbox = emails.getFirst();
        assertThat(inbox.getAccount().getEmailAddress().value()).isEqualTo("orders@nordhafen.example");
        assertThat(inbox.getFolder()).isEqualTo("inbox");
        assertThat(inbox.getProcessingState()).isEqualTo(ProcessingState.NEW);
        assertThat(inbox.getConversationId()).isEqualTo("c-halvorsen");
        assertThat(inbox.getProviderMessageId()).isEqualTo("demo-m-0001");
        assertThat(inbox.getCcRecipients()).extracting(r -> r.address()).containsExactly("alex.morgan@nordhafen.example");
        assertThat(inbox.getReceivedAt()).isEqualTo(Instant.parse("2026-07-06T11:40:00Z"));
        assertThat(inbox.getSnippet()).isEqualTo("Dear Nordhafen team, please find our purchase order HV-6120.");
        EmailMessage sent = emails.get(1);
        assertThat(sent.getFolder()).isEqualTo(EmailMessage.FOLDER_SENT);
        assertThat(sent.getProcessingState()).isEqualTo(ProcessingState.SKIPPED);
    }

    @Test
    void importMailbox_ShiftsDatesByWholeWeeksSoTheLastMailIsRecent() {
        emptySchema();

        service("2026-10-13T08:00:00Z").importMailbox(owner, mailbox, true);

        List<EmailMessage> emails = savedEmails();
        // last mail Fri 2026-07-31 → 10 whole weeks later: Fri 2026-10-09 (same weekday, within the last week)
        assertThat(emails.get(1).getReceivedAt()).isEqualTo(Instant.parse("2026-10-09T14:00:00Z"));
        assertThat(emails.getFirst().getReceivedAt()).isEqualTo(Instant.parse("2026-09-14T11:40:00Z"));
    }

    @Test
    void importMailbox_KeepsExistingCollectionsAndRelationTypes() {
        List<CollectionDto> existing = new ArrayList<>(BusinessCollections.commands().stream()
                .map(command -> new CollectionDto().id(1L).slug(command.getSlug())).toList());
        existing.add(new CollectionDto().id(2L).slug("people"));
        existing.add(new CollectionDto().id(3L).slug("organizations"));
        when(collectionSchemaService.findAll()).thenReturn(existing);
        when(relationTypeService.findAll()).thenReturn(BusinessCollections.relationTypes().stream()
                .map(spec -> new RelationTypeDto().slug(spec.slug())).toList());
        when(mailAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service("2026-10-13T08:00:00Z").importMailbox(owner, mailbox, false);

        verify(collectionSchemaService, never()).createCollection(any());
        verify(relationTypeService, never()).createRelationType(any());
    }

    @Test
    void importMailbox_DescribesTheDemoCompanyInTheAiSettings() {
        emptySchema();

        service("2026-10-13T08:00:00Z").importMailbox(owner, mailbox, false);

        ArgumentCaptor<AiSettings> settings = ArgumentCaptor.forClass(AiSettings.class);
        verify(aiSettingsRepository).save(settings.capture());
        assertThat(settings.getValue().getOwnerProfile()).isEqualTo(DemoMailboxService.OWNER_PROFILE).contains("Nordhafen");
        assertThat(settings.getValue().getUser()).isSameAs(owner);
    }

    @Test
    void importMailbox_KeepsAProfileTheUserWrote() {
        emptySchema();
        AiSettings own = AiSettings.defaults(owner);
        own.setOwnerProfile("My own words");
        when(aiSettingsRepository.findByUser_Id(owner.getId())).thenReturn(Optional.of(own));

        service("2026-10-13T08:00:00Z").importMailbox(owner, mailbox, false);

        assertThat(own.getOwnerProfile()).isEqualTo("My own words");
        verify(aiSettingsRepository, never()).save(any());
    }

    @Test
    void importMailbox_SkipsAnOwnerWhoAlreadyHasTheDemoMailbox() {
        when(mailAccountRepository.existsByOwner_IdAndProvider(owner.getId(), MailProvider.DEMO)).thenReturn(true);

        int imported = service("2026-10-13T08:00:00Z").importMailbox(owner, mailbox, false);

        assertThat(imported).isZero();
        verify(mailAccountRepository, never()).save(any());
        verify(emailMessageRepository, never()).saveAll(any());
    }

    @Test
    void importMailbox_MissingFolderRejected() {
        assertThatThrownBy(() -> service("2026-10-13T08:00:00Z").importMailbox(owner, mailbox.resolve("nope"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }
}
