package com.example.contor.service;

import com.example.contor.ai.MailContext;
import com.example.contor.ai.ReplyThread;
import com.example.contor.api.model.RecordDto;
import com.example.contor.config.AiProperties;
import com.example.contor.config.MailContextProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Collection;
import com.example.contor.domain.CollectionField;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.FieldType;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.RecordSource;
import com.example.contor.domain.Slug;
import com.example.contor.exception.RecordNotFoundException;
import com.example.contor.persistence.EmailEmbeddingRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.NearEmail;
import com.example.contor.persistence.RecordSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailContextServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final Instant RECEIVED = Instant.parse("2026-10-06T08:00:00Z");

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private EmailEmbeddingRepository emailEmbeddingRepository;

    @Mock
    private RecordSourceRepository recordSourceRepository;

    @Mock
    private RecordService recordService;

    private MailAccount account;
    private MailContextService service;

    private static AiProperties aiProperties() {
        return new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Berlin"));
    }

    private static MailContextProperties contextProperties(boolean enabled) {
        return new MailContextProperties(enabled, 6000, 5, 5, 0.25, Duration.ofDays(180));
    }

    @BeforeEach
    void setUp() {
        account = MailAccount.builder().id(new MailAccount.MailAccountId(2L)).owner(AppUser.builder().id(OWNER).build())
                .emailAddress(new Email("me@example.com")).build();
        service = new MailContextService(emailMessageRepository, emailEmbeddingRepository, recordSourceRepository, recordService,
                aiProperties(), contextProperties(true));
    }

    private EmailMessage email(long id, String conversation, String folder, Instant receivedAt) {
        EmailMessage email = EmailMessage.builder().id(new EmailMessage.EmailMessageId(id)).account(account).conversationId(conversation)
                .folder(folder).fromName("Anna").fromAddress("anna@acme.com").subject("Subject " + id).bodyText("Body " + id)
                .receivedAt(receivedAt).build();
        lenient().when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(id))).thenReturn(Optional.of(email));
        return email;
    }

    private static List<Long> ids(List<ReplyThread.Message> messages) {
        return messages.stream().map(message -> Long.parseLong(message.subject().substring("Subject ".length()))).toList();
    }

    @Test
    void contextFor_ThreadBeforeTheEmailSenderHistoryRelatedMailAndTheirRecords() {
        EmailMessage target = email(10L, "c1", "inbox", RECEIVED);
        EmailMessage earlier = email(8L, "c1", "inbox", RECEIVED.minusSeconds(7200));
        EmailMessage reply = email(9L, "c1", "sent", RECEIVED.minusSeconds(3600));
        EmailMessage later = email(11L, "c1", "inbox", RECEIVED.plusSeconds(3600));
        EmailMessage sameConversation = email(7L, "c1", "inbox", RECEIVED.minusSeconds(9000));
        EmailMessage fromAnna = email(5L, "c2", "inbox", RECEIVED.minus(Duration.ofDays(3)));
        EmailMessage relatedMail = email(4L, "c3", "inbox", RECEIVED.minus(Duration.ofDays(9)));
        when(emailMessageRepository.findThread(account.getId(), "c1")).thenReturn(List.of(sameConversation, earlier, reply, target, later));
        when(emailMessageRepository.findFromSenderBefore(eq(OWNER), eq("anna@acme.com"), eq(RECEIVED), any()))
                .thenReturn(List.of(sameConversation, fromAnna));
        when(emailEmbeddingRepository.findNearest(eq(OWNER), eq(10L), eq("embedder"), eq(RECEIVED.minus(Duration.ofDays(180))),
                eq(RECEIVED), eq(0.25), anyInt()))
                .thenReturn(List.of(new NearEmail(5L, 0.1), new NearEmail(4L, 0.15), new NearEmail(7L, 0.2)));
        when(emailMessageRepository.findAllById(List.of(new EmailMessage.EmailMessageId(4L)))).thenReturn(List.of(relatedMail));
        Collection events = Collection.builder().id(new Collection.CollectionId(3L)).slug(new Slug("events")).build();
        when(recordSourceRepository.findByEmail_IdIn(anyList()))
                .thenReturn(List.of(RecordSource.builder().collection(events).recordId(77L).email(fromAnna).build()));
        when(recordService.getRecord(3L, 77L)).thenReturn(new RecordDto(77L, 3L, new LinkedHashMap<>(), new LinkedHashMap<>(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), "AI").title("Interview at Acme"));

        MailContext context = service.contextFor(10L);

        assertThat(ids(context.earlierInThread())).containsExactly(7L, 8L, 9L);
        assertThat(context.earlierInThread().getLast().own()).isTrue();
        assertThat(ids(context.fromSender())).containsExactly(5L);
        assertThat(ids(context.related())).containsExactly(4L);
        assertThat(context.records()).containsExactly("events: Interview at Acme");
    }

    @Test
    void contextFor_RecordsShowTheirKeysAndStatusSoAFollowUpCanReferToThem() {
        EmailMessage target = email(10L, "c1", "inbox", RECEIVED);
        EmailMessage earlier = email(8L, "c1", "inbox", RECEIVED.minusSeconds(7200));
        when(emailMessageRepository.findThread(account.getId(), "c1")).thenReturn(List.of(earlier, target));
        Collection orders = Collection.builder().id(new Collection.CollectionId(6L)).slug(new Slug("sales_orders"))
                .matchKeys(List.of("order_number")).build();
        orders.getFields().add(CollectionField.builder().slug(new Slug("state")).type(FieldType.STATUS).build());
        orders.getFields().add(CollectionField.builder().slug(new Slug("customer")).type(FieldType.TEXT).build());
        when(recordSourceRepository.findByEmail_IdIn(anyList()))
                .thenReturn(List.of(RecordSource.builder().collection(orders).recordId(12L).email(earlier).build()));
        when(recordService.getRecord(6L, 12L)).thenReturn(new RecordDto(12L, 6L,
                new LinkedHashMap<>(Map.of("order_number", "SO-26-0155", "state", "shipped", "customer", "Kessler")), new LinkedHashMap<>(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), "AI").title("Kessler SO-26-0155"));

        assertThat(service.contextFor(10L).records()).containsExactly("sales_orders: Kessler SO-26-0155 (order_number: SO-26-0155, state: shipped)");
    }

    @Test
    void contextTextFor_RendersTheContextWithinTheBudgetAndIsEmptyWithoutOne() {
        EmailMessage target = email(10L, null, "inbox", RECEIVED);
        EmailMessage fromAnna = email(5L, "c2", "inbox", RECEIVED.minus(Duration.ofDays(3)));
        when(emailMessageRepository.findFromSenderBefore(eq(OWNER), eq("anna@acme.com"), eq(RECEIVED), any())).thenReturn(List.of(fromAnna));

        String text = service.contextTextFor(target.getId().id());

        assertThat(text).contains("Earlier emails").contains("Subject 5").hasSizeLessThanOrEqualTo(6000);
        assertThat(service.contextTextFor(99L)).isEmpty();
    }

    @Test
    void contextTextWithoutThread_LeavesTheConversationOutForCallersThatHaveIt() {
        EmailMessage target = email(10L, "c1", "inbox", RECEIVED);
        EmailMessage earlier = email(8L, "c1", "inbox", RECEIVED.minusSeconds(7200));
        EmailMessage fromAnna = email(5L, "c2", "inbox", RECEIVED.minus(Duration.ofDays(3)));
        when(emailMessageRepository.findThread(account.getId(), "c1")).thenReturn(List.of(earlier, target));
        when(emailMessageRepository.findFromSenderBefore(eq(OWNER), eq("anna@acme.com"), eq(RECEIVED), any())).thenReturn(List.of(fromAnna));

        String text = service.contextTextWithoutThread(10L);

        assertThat(text).contains("Subject 5").doesNotContain("Subject 8");
    }

    @Test
    void contextFor_ARecordThatCannotBeReadIsLeftOut() {
        EmailMessage target = email(10L, null, "inbox", RECEIVED);
        EmailMessage fromAnna = email(5L, "c2", "inbox", RECEIVED.minus(Duration.ofDays(3)));
        when(emailMessageRepository.findFromSenderBefore(eq(OWNER), eq("anna@acme.com"), eq(RECEIVED), any())).thenReturn(List.of(fromAnna));
        Collection events = Collection.builder().id(new Collection.CollectionId(3L)).slug(new Slug("events")).build();
        when(recordSourceRepository.findByEmail_IdIn(anyList()))
                .thenReturn(List.of(RecordSource.builder().collection(events).recordId(77L).email(fromAnna).build()));
        when(recordService.getRecord(3L, 77L)).thenThrow(new RecordNotFoundException(3L, 77L));

        MailContext context = service.contextFor(target.getId().id());

        assertThat(ids(context.fromSender())).containsExactly(5L);
        assertThat(context.records()).isEmpty();
    }

    @Test
    void contextFor_SentMailAndUnknownEmailsHaveNoContext() {
        email(10L, "c1", "sent", RECEIVED);

        assertThat(service.contextFor(10L)).isEqualTo(MailContext.EMPTY);
        assertThat(service.contextFor(99L)).isEqualTo(MailContext.EMPTY);
    }

    @Test
    void contextFor_TurnedOffReadsNothing() {
        service = new MailContextService(emailMessageRepository, emailEmbeddingRepository, recordSourceRepository, recordService,
                aiProperties(), contextProperties(false));

        assertThat(service.contextFor(10L)).isEqualTo(MailContext.EMPTY);
        verifyNoInteractions(emailMessageRepository, emailEmbeddingRepository);
    }

    @Test
    void contextFor_NoRelatedMailWhenTheSearchFindsNothing() {
        email(10L, null, "inbox", RECEIVED);
        when(emailEmbeddingRepository.findNearest(eq(OWNER), eq(10L), eq("embedder"), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of());

        MailContext context = service.contextFor(10L);

        assertThat(context.related()).isEmpty();
        assertThat(context.isEmpty()).isTrue();
    }
}
