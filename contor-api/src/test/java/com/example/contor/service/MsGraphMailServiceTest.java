package com.example.contor.service;

import com.microsoft.graph.models.Contact;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MsGraphMailServiceTest {

    @Test
    void extractEmail_PrefersMailOverUserPrincipalName() {
        var user = new User();
        user.setMail("work@company.com");
        user.setUserPrincipalName("work_upn@company.com");
        user.setDisplayName("Test User");

        var profile = MsGraphMailService.extractProfile(user);
        assertThat(profile.emailAddress()).isEqualTo("work@company.com");
        assertThat(profile.displayName()).isEqualTo("Test User");
    }

    @Test
    void extractEmail_FallsBackToUserPrincipalNameWhenMailNull() {
        var user = new User();
        user.setMail(null);
        user.setUserPrincipalName("user@outlook.com");
        user.setDisplayName("Personal User");

        var profile = MsGraphMailService.extractProfile(user);
        assertThat(profile.emailAddress()).isEqualTo("user@outlook.com");
        assertThat(profile.displayName()).isEqualTo("Personal User");
    }

    @Test
    void mapRecipients_ConvertsGraphRecipientsToDomain() {
        var r1 = new Recipient();
        var addr1 = new EmailAddress();
        addr1.setName("Alice");
        addr1.setAddress("alice@example.com");
        r1.setEmailAddress(addr1);

        var recipients = MsGraphMailService.mapRecipients(List.of(r1));
        assertThat(recipients).hasSize(1);
        assertThat(recipients.get(0).name()).isEqualTo("Alice");
        assertThat(recipients.get(0).address()).isEqualTo("alice@example.com");
    }

    @Test
    void contentTypeOf_DetectsImageFormatFromBytes() {
        assertThat(MsGraphMailService.contentTypeOf(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D})).isEqualTo("image/png");
        assertThat(MsGraphMailService.contentTypeOf(new byte[] {'G', 'I', 'F', '8', '9'})).isEqualTo("image/gif");
        assertThat(MsGraphMailService.contentTypeOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).isEqualTo("image/jpeg");
        assertThat(MsGraphMailService.contentTypeOf(new byte[] {1, 2})).isEqualTo("image/jpeg");
    }

    @Test
    void contactAddresses_AreLowerCasedDistinctAndValid() {
        var contact = new Contact();
        var a = new EmailAddress();
        a.setAddress("Bob@Example.com");
        var b = new EmailAddress();
        b.setAddress("bob@example.com");
        var c = new EmailAddress();
        c.setAddress("not an address");
        contact.setEmailAddresses(List.of(a, b, c, new EmailAddress()));

        assertThat(MsGraphMailService.contactAddresses(contact)).containsExactly("bob@example.com");
        assertThat(MsGraphMailService.contactAddresses(new Contact())).isEmpty();
    }
}
