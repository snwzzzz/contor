package com.example.contor.mailprovider;

import com.example.contor.domain.EmailRecipient;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.SyncFolder;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.Contact;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.User;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MicrosoftGraphMailboxClientTest {

    @Test
    void is_the_microsoft_graph_provider() {
        assertThat(new MicrosoftGraphMailboxClient().provider()).isEqualTo(MailProvider.MICROSOFT_GRAPH);
    }

    @Test
    void profile_prefers_mail_over_user_principal_name() {
        var user = new User();
        user.setMail("work@company.com");
        user.setUserPrincipalName("work_upn@company.com");
        user.setDisplayName("Test User");

        var profile = MicrosoftGraphMailboxClient.extractProfile(user);
        assertThat(profile.emailAddress()).isEqualTo("work@company.com");
        assertThat(profile.displayName()).isEqualTo("Test User");
    }

    @Test
    void profile_falls_back_to_user_principal_name_when_mail_is_null() {
        var user = new User();
        user.setMail(null);
        user.setUserPrincipalName("user@outlook.com");
        user.setDisplayName("Personal User");

        var profile = MicrosoftGraphMailboxClient.extractProfile(user);
        assertThat(profile.emailAddress()).isEqualTo("user@outlook.com");
        assertThat(profile.displayName()).isEqualTo("Personal User");
    }

    @Test
    void recipients_are_mapped_to_the_domain() {
        var recipients = MicrosoftGraphMailboxClient.mapRecipients(List.of(recipient("Alice", "alice@example.com"), new Recipient()));

        assertThat(recipients).containsExactly(new EmailRecipient("Alice", "alice@example.com"));
    }

    @Test
    void contact_addresses_are_lower_cased_distinct_and_valid() {
        var contact = new Contact();
        var a = new EmailAddress();
        a.setAddress("Bob@Example.com");
        var b = new EmailAddress();
        b.setAddress("bob@example.com");
        var c = new EmailAddress();
        c.setAddress("not an address");
        contact.setEmailAddresses(List.of(a, b, c, new EmailAddress()));

        assertThat(MicrosoftGraphMailboxClient.contactAddresses(contact)).containsExactly("bob@example.com");
        assertThat(MicrosoftGraphMailboxClient.contactAddresses(new Contact())).isEmpty();
    }

    @Test
    void inbox_message_is_mapped_with_its_received_time_and_html_body() {
        var received = OffsetDateTime.parse("2026-10-07T09:00:00Z");
        var message = new Message();
        message.setId("m1");
        message.setInternetMessageId("<abc@mail>");
        message.setConversationId("conv-1");
        message.setSubject("Hello");
        message.setBodyPreview("Hi there");
        message.setFrom(recipient("Alice", "alice@example.com"));
        message.setToRecipients(List.of(recipient("Me", "me@example.com")));
        message.setReceivedDateTime(received);
        message.setSentDateTime(received.minusMinutes(1));
        message.setIsRead(false);
        message.setHasAttachments(true);
        message.setBody(body(BodyType.Html, "<p>Hi there</p>"));

        SyncedMessage synced = MicrosoftGraphMailboxClient.toSyncedMessage(message, SyncFolder.INBOX);

        assertThat(synced.providerMessageId()).isEqualTo("m1");
        assertThat(synced.internetMessageId()).isEqualTo("<abc@mail>");
        assertThat(synced.conversationId()).isEqualTo("conv-1");
        assertThat(synced.from()).isEqualTo(new EmailRecipient("Alice", "alice@example.com"));
        assertThat(synced.to()).containsExactly(new EmailRecipient("Me", "me@example.com"));
        assertThat(synced.cc()).isEmpty();
        assertThat(synced.subject()).isEqualTo("Hello");
        assertThat(synced.snippet()).isEqualTo("Hi there");
        assertThat(synced.bodyHtml()).isEqualTo("<p>Hi there</p>");
        assertThat(synced.bodyText()).isNull();
        assertThat(synced.receivedAt()).isEqualTo(received.toInstant());
        assertThat(synced.read()).isFalse();
        assertThat(synced.hasAttachments()).isTrue();
    }

    @Test
    void sent_message_uses_its_sent_time_and_text_body() {
        var sentAt = OffsetDateTime.parse("2026-10-07T09:00:00Z");
        var message = new Message();
        message.setId("s1");
        message.setSentDateTime(sentAt);
        message.setReceivedDateTime(sentAt.plusSeconds(5));
        message.setBody(body(BodyType.Text, "Thanks, Friday works."));

        SyncedMessage synced = MicrosoftGraphMailboxClient.toSyncedMessage(message, SyncFolder.SENT);

        assertThat(synced.receivedAt()).isEqualTo(sentAt.toInstant());
        assertThat(synced.bodyText()).isEqualTo("Thanks, Friday works.");
        assertThat(synced.bodyHtml()).isNull();
        assertThat(synced.from()).isNull();
    }

    private static Recipient recipient(String name, String address) {
        var recipient = new Recipient();
        var emailAddress = new EmailAddress();
        emailAddress.setName(name);
        emailAddress.setAddress(address);
        recipient.setEmailAddress(emailAddress);
        return recipient;
    }

    private static ItemBody body(BodyType type, String content) {
        var body = new ItemBody();
        body.setContentType(type);
        body.setContent(content);
        return body;
    }
}
