package com.example.contor.service;

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
}
