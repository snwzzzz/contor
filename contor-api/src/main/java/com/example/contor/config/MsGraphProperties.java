package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties("app.mail.msgraph")
public record MsGraphProperties(
        String clientId,
        String clientSecret,
        @DefaultValue("common") String tenantId,
        @DefaultValue("http://localhost:8080/api/mail/accounts/callback") String redirectUri,
        @DefaultValue("Mail.Read,User.Read,offline_access") String scopes) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public Set<String> scopeSet() {
        if (scopes == null || scopes.isBlank()) {
            return Set.of("Mail.Read", "User.Read", "offline_access");
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String authority() {
        return "https://login.microsoftonline.com/" + (tenantId != null && !tenantId.isBlank() ? tenantId : "common") + "/";
    }

    public String authorizeEndpoint() {
        return authority() + "oauth2/v2.0/authorize";
    }

    public String tokenEndpoint() {
        return authority() + "oauth2/v2.0/token";
    }
}
