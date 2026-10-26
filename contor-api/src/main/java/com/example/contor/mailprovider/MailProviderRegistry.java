package com.example.contor.mailprovider;

import com.example.contor.domain.MailProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Collects every {@link MailOAuthClient} and {@link MailboxClient} bean, keyed by provider. */
@Component
public class MailProviderRegistry {

    private final Map<MailProvider, MailOAuthClient> oauthClients;
    private final Map<MailProvider, MailboxClient> mailboxClients;

    public MailProviderRegistry(List<MailOAuthClient> oauthClients, List<MailboxClient> mailboxClients) {
        this.oauthClients = byProvider(oauthClients, MailOAuthClient::provider, "MailOAuthClient");
        this.mailboxClients = byProvider(mailboxClients, MailboxClient::provider, "MailboxClient");
        if (!this.oauthClients.keySet().equals(this.mailboxClients.keySet())) {
            throw new IllegalStateException("Every mail provider needs a MailOAuthClient and a MailboxClient; OAuth: "
                    + this.oauthClients.keySet() + ", mailbox: " + this.mailboxClients.keySet());
        }
    }

    private static <T> Map<MailProvider, T> byProvider(List<T> clients, Function<T, MailProvider> provider, String kind) {
        Map<MailProvider, T> map = new EnumMap<>(MailProvider.class);
        for (T client : clients) {
            T previous = map.putIfAbsent(provider.apply(client), client);
            if (previous != null) {
                throw new IllegalStateException("Two " + kind + "s for " + provider.apply(client) + ": "
                        + previous.getClass().getSimpleName() + ", " + client.getClass().getSimpleName());
            }
        }
        return map;
    }

    public MailOAuthClient oauth(MailProvider provider) {
        MailOAuthClient client = oauthClients.get(provider);
        if (client == null) {
            throw new IllegalStateException("No MailOAuthClient for " + provider);
        }
        return client;
    }

    public MailboxClient mailbox(MailProvider provider) {
        MailboxClient client = mailboxClients.get(provider);
        if (client == null) {
            throw new IllegalStateException("No MailboxClient for " + provider);
        }
        return client;
    }

    /** The connectable providers in {@link MailProvider} order. */
    public List<MailOAuthClient> oauthClients() {
        return List.copyOf(oauthClients.values());
    }
}
