package com.example.contor.mailprovider;

import com.example.contor.config.GmailProperties;
import com.example.contor.domain.MailProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sign-in with Google for Gmail mailboxes (OAuth 2.0 for web server applications). {@code access_type=offline} with
 * {@code prompt=consent} makes Google return a refresh token on every connect, also when the user consented before.
 */
@Component
public class GmailOAuthClient implements MailOAuthClient {

    static final String AUTHORIZE_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final GmailProperties properties;
    private final RestClient restClient;

    @Autowired
    public GmailOAuthClient(GmailProperties properties) {
        this(properties, RestClient.builder());
    }

    GmailOAuthClient(GmailProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public MailProvider provider() {
        return MailProvider.GMAIL;
    }

    @Override
    public String displayName() {
        return "Gmail";
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String authorizeUrl(String state, String codeChallenge, String loginHint) {
        requireConfigured();
        StringBuilder sb = new StringBuilder(AUTHORIZE_ENDPOINT)
                .append("?client_id=").append(encode(properties.clientId()))
                .append("&redirect_uri=").append(encode(properties.redirectUri()))
                .append("&response_type=code")
                .append("&scope=").append(encode(properties.scopeParameter()))
                .append("&access_type=offline")
                .append("&prompt=consent")
                .append("&include_granted_scopes=true")
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
        return OAuthTokenResponse.post(restClient, TOKEN_ENDPOINT, formData, "Google");
    }

    /** Google keeps the refresh token valid and sends none back; a revoked one answers 400 invalid_grant. */
    @Override
    public OAuthTokens refresh(String refreshToken) {
        requireConfigured();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", properties.clientId());
        formData.add("client_secret", properties.clientSecret());
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        return OAuthTokenResponse.post(restClient, TOKEN_ENDPOINT, formData, "Google");
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Gmail is not configured (GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET required)");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
