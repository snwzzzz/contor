package com.example.contor.mailprovider;

import com.example.contor.domain.MailProvider;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailProviderRegistryTest {

    private static MailOAuthClient oauth(MailProvider provider) {
        MailOAuthClient client = mock(MailOAuthClient.class);
        when(client.provider()).thenReturn(provider);
        return client;
    }

    private static MailboxClient mailbox(MailProvider provider) {
        MailboxClient client = mock(MailboxClient.class);
        when(client.provider()).thenReturn(provider);
        return client;
    }

    @Test
    void resolves_the_clients_of_each_provider() {
        MailOAuthClient graphOAuth = oauth(MailProvider.MICROSOFT_GRAPH);
        MailOAuthClient gmailOAuth = oauth(MailProvider.GMAIL);
        MailboxClient graphMailbox = mailbox(MailProvider.MICROSOFT_GRAPH);
        MailboxClient gmailMailbox = mailbox(MailProvider.GMAIL);

        var registry = new MailProviderRegistry(List.of(gmailOAuth, graphOAuth), List.of(graphMailbox, gmailMailbox));

        assertThat(registry.oauth(MailProvider.GMAIL)).isSameAs(gmailOAuth);
        assertThat(registry.mailbox(MailProvider.MICROSOFT_GRAPH)).isSameAs(graphMailbox);
        assertThat(registry.oauthClients()).containsExactly(graphOAuth, gmailOAuth);
    }

    @Test
    void unknown_provider_is_rejected() {
        var registry = new MailProviderRegistry(List.of(oauth(MailProvider.GMAIL)), List.of(mailbox(MailProvider.GMAIL)));

        assertThatThrownBy(() -> registry.oauth(MailProvider.DEMO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry.mailbox(MailProvider.MICROSOFT_GRAPH)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void two_clients_for_one_provider_fail_at_startup() {
        assertThatThrownBy(() -> new MailProviderRegistry(
                List.of(oauth(MailProvider.GMAIL), oauth(MailProvider.GMAIL)), List.of(mailbox(MailProvider.GMAIL))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GMAIL");
    }

    @Test
    void a_provider_without_its_mailbox_client_fails_at_startup() {
        assertThatThrownBy(() -> new MailProviderRegistry(List.of(oauth(MailProvider.GMAIL)), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GMAIL");
    }
}
