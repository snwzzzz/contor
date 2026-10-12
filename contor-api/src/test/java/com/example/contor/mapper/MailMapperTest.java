package com.example.contor.mapper;

import com.example.contor.api.model.EmailDto;
import com.example.contor.api.model.EmailLabelDto;
import com.example.contor.api.model.EmailProcessingState;
import com.example.contor.api.model.EmailSummaryDto;
import com.example.contor.api.model.LabelOrigin;
import com.example.contor.api.model.MailAccountDto;
import com.example.contor.api.model.MailAccountState;
import com.example.contor.api.model.MailProviderKind;
import com.example.contor.api.model.ReplyMarker;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailLabel;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.HexColor;
import com.example.contor.domain.Label;
import com.example.contor.domain.LabelSource;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailAccountStatus;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.Slug;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class MailMapperTest {

    private static MailAccount account() {
        return MailAccount.builder().id(new MailAccount.MailAccountId(2L)).provider(MailProvider.MICROSOFT_GRAPH)
                .emailAddress(new Email("me@example.com")).displayName("Work").status(MailAccountStatus.NEEDS_REAUTH)
                .accessToken("secret-access").refreshToken("secret-refresh").lastSyncedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
    }

    private static EmailMessage email() {
        return EmailMessage.builder().id(new EmailMessage.EmailMessageId(5L)).account(account()).providerMessageId("AAMk")
                .internetMessageId("<m@x>").conversationId("c-1").folder("inbox").fromAddress("shop@example.com").fromName("Shop")
                .toRecipients(List.of(new EmailRecipient("Me", "me@example.com"))).subject("Shipped").snippet("Your parcel")
                .bodyText("text").bodyHtml("<p>html</p>").receivedAt(Instant.parse("2026-01-01T10:00:00Z")).read(true)
                .hasAttachments(true).processingState(ProcessingState.DONE).processingError(null).build();
    }

    private static EmailLabelDto label() {
        var label = Label.builder().id(new Label.LabelId(3L)).slug(new Slug("orders")).name("Orders").color(new HexColor("#00ff00")).build();
        var emailLabel = EmailLabel.builder().id(new EmailLabel.EmailLabelId(5L, 3L)).source(LabelSource.AI).confidence(new BigDecimal("0.9000")).build();
        return MailMapper.toDto(emailLabel, label);
    }

    @Test
    void maps_mail_account_without_tokens() {
        MailAccountDto dto = MailMapper.toDto(account());

        assertEquals(2L, dto.getId());
        assertEquals(MailProviderKind.MICROSOFT_GRAPH, dto.getProvider());
        assertEquals("me@example.com", dto.getEmailAddress());
        assertEquals("Work", dto.getDisplayName());
        assertEquals(MailAccountState.NEEDS_REAUTH, dto.getStatus());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC), dto.getLastSyncedAt());
        assertFalse(dto.toString().contains("secret"));
        assertFalse(dto.getHasPhoto());
    }

    @Test
    void maps_whether_the_account_has_a_profile_photo() {
        var account = account();
        account.setPhoto(new byte[] {1, 2, 3});

        assertTrue(MailMapper.toDto(account).getHasPhoto());
    }

    @Test
    void maps_email_label() {
        EmailLabelDto dto = label();

        assertEquals(3L, dto.getLabelId());
        assertEquals("orders", dto.getSlug());
        assertEquals("#00ff00", dto.getColor());
        assertEquals(LabelOrigin.AI, dto.getSource());
        assertEquals(new BigDecimal("0.9"), dto.getConfidence());
    }

    @Test
    void maps_email_summary_without_bodies() {
        EmailSummaryDto dto = MailMapper.toSummaryDto(email(), List.of(label()));

        assertEquals(5L, dto.getId());
        assertEquals(2L, dto.getAccountId());
        assertEquals("Shipped", dto.getSubject());
        assertEquals("Shop", dto.getFromName());
        assertTrue(dto.getRead());
        assertTrue(dto.getHasAttachments());
        assertEquals(EmailProcessingState.DONE, dto.getProcessingState());
        assertEquals(1, dto.getLabels().size());
    }

    @Test
    void maps_the_to_recipients_of_a_summary() {
        EmailSummaryDto dto = MailMapper.toSummaryDto(email(), List.of());

        assertEquals(1, dto.getToRecipients().size());
        assertEquals("me@example.com", dto.getToRecipients().get(0).getAddress());
    }

    @Test
    void maps_thread_size_and_answered_of_a_summary() {
        EmailSummaryDto threaded = MailMapper.toSummaryDto(email(), List.of(), 3, true, ReplyMarker.DRAFT_READY);
        EmailSummaryDto alone = MailMapper.toSummaryDto(email(), List.of());

        assertEquals(3, threaded.getThreadSize());
        assertTrue(threaded.getAnswered());
        assertEquals(ReplyMarker.DRAFT_READY, threaded.getReplyState());
        assertEquals(1, alone.getThreadSize());
        assertFalse(alone.getAnswered());
        assertNull(alone.getReplyState());
    }

    @Test
    void maps_email_with_bodies_and_recipients() {
        EmailDto dto = MailMapper.toDto(email(), List.of());

        assertEquals("<m@x>", dto.getInternetMessageId());
        assertEquals("text", dto.getBodyText());
        assertEquals("<p>html</p>", dto.getBodyHtml());
        assertEquals("me@example.com", dto.getToRecipients().get(0).getAddress());
        assertTrue(dto.getCcRecipients().isEmpty());
        assertEquals(Instant.parse("2026-01-01T10:00:00Z").atOffset(ZoneOffset.UTC), dto.getReceivedAt());
    }

    @Test
    void handles_null_input() {
        assertNull(MailMapper.toDto((MailAccount) null));
        assertNull(MailMapper.toDto((EmailMessage) null, List.of()));
        assertNull(MailMapper.toSummaryDto(null, List.of()));
    }

    @Test
    void handles_null_id_and_account() {
        var dto = MailMapper.toSummaryDto(EmailMessage.builder().build(), List.of());

        assertNull(dto.getId());
        assertNull(dto.getAccountId());
    }
}
