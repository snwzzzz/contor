package com.example.contor.mailprovider;

import com.example.contor.config.MsGraphProperties;
import com.example.contor.domain.MailProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Sign-in with Microsoft Entra ID (work and personal Microsoft accounts) for Outlook mailboxes. */
@Component
public class MicrosoftGraphOAuthClient implements MailOAuthClient {

    private final MsGraphProperties properties;
    private final RestClient restClient;

    @Autowired
    public MicrosoftGraphOAuthClient(MsGraphProperties properties) {
        this(properties, RestClient.builder());
    }

    MicrosoftGraphOAuthClient(MsGraphProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public MailProvider provider() {
        return MailProvider.MICROSOFT_GRAPH;
    }

    @Override
    public String displayName() {
        return "Outlook";
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String authorizeUrl(String state, String codeChallenge, String loginHint) {
        requireConfigured();
        StringBuilder sb = new StringBuilder(properties.authorizeEndpoint())
                .append("?client_id=").append(encode(properties.clientId()))
                .append("&response_type=code")
                .append("&redirect_uri=").append(encode(properties.redirectUri()))
                .append("&response_mode=query")
                .append("&scope=").append(encode(properties.scopeParameter()))
                .append("&state=").append(encode(state))
                .append("&code_challenge=").append(encode(codeChallenge))
                .append("&code_challenge_method=S256");
        if (loginHint != null && !loginHint.isBlank()) {
            sb.append("&login_hint=").append(encode(loginHint));
        }
        return sb.toString();
    }

    @Override
    public OAuthTokens exchangeCode(String code, String codeVerifier) {
        requireConfigured();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", properties.clientId());
        formData.add("client_secret", properties.clientSecret());
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("code_verifier", codeVerifier);
        formData.add("redirect_uri", properties.redirectUri());
        formData.add("scope", properties.scopeParameter());
        return OAuthTokenResponse.post(restClient, properties.tokenEndpoint(), formData, "Microsoft Entra ID");
    }

    @Override
    public OAuthTokens refresh(String refreshToken) {
        requireConfigured();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", properties.clientId());
        formData.add("client_secret", properties.clientSecret());
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        formData.add("scope", properties.scopeParameter());
        return OAuthTokenResponse.post(restClient, properties.tokenEndpoint(), formData, "Microsoft Entra ID");
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Microsoft Graph is not configured (MSGRAPH_CLIENT_ID and MSGRAPH_CLIENT_SECRET required)");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
