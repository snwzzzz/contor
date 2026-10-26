package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gmail sign-in (Google OAuth client of type "Web application") and sync. {@code initialSyncDays}: how far back the
 * first sync of a mailbox reads (later syncs read the Gmail history, so nothing newer is missed).
 */
@ConfigurationProperties("app.mail.gmail")
public record GmailProperties(
        String clientId,
        String clientSecret,
        @DefaultValue("http://localhost:8080/api/mail/accounts/callback") String redirectUri,
        @DefaultValue(GmailProperties.DEFAULT_SCOPES) String scopes,
        @DefaultValue("365") int initialSyncDays) {

    public static final String DEFAULT_SCOPES = "openid email profile https://www.googleapis.com/auth/gmail.readonly "
            + "https://www.googleapis.com/auth/contacts.readonly";

    public GmailProperties {
        if (initialSyncDays <= 0) {
            initialSyncDays = 365;
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    /** OAuth {@code scope} parameter: space-separated (RFC 6749); configured comma- or space-separated. */
    public String scopeParameter() {
        String configured = scopes == null || scopes.isBlank() ? DEFAULT_SCOPES : scopes;
        return Stream.of(configured.split("[,\\s]+"))
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(" "));
    }
}
