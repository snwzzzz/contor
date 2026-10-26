package com.example.contor.mailprovider;

import com.example.contor.config.GmailProperties;
import com.example.contor.domain.MailProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GmailOAuthClientTest {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private MockRestServiceServer server;
    private GmailOAuthClient client;

    @BeforeEach
    void setUp() {
        var properties = new GmailProperties("cid.apps.googleusercontent.com", "csecret",
                "http://localhost:8080/api/mail/accounts/callback", "openid email https://www.googleapis.com/auth/gmail.readonly", 365);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GmailOAuthClient(properties, builder);
    }

    @Test
    void is_the_gmail_provider() {
        assertThat(client.provider()).isEqualTo(MailProvider.GMAIL);
        assertThat(client.displayName()).isEqualTo("Gmail");
        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    void authorize_url_asks_for_offline_access_with_consent_and_pkce() {
        String url = client.authorizeUrl("st", "challenge", "me@gmail.com");

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(url).contains(
                "client_id=cid.apps.googleusercontent.com",
                "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fmail%2Faccounts%2Fcallback",
                "response_type=code",
                "scope=openid+email+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fgmail.readonly",
                "access_type=offline",
                "prompt=consent",
                "include_granted_scopes=true",
                "state=st",
                "code_challenge=challenge",
                "code_challenge_method=S256",
                "login_hint=me%40gmail.com");
    }

    @Test
    void authorize_url_without_hint_has_no_login_hint() {
        assertThat(client.authorizeUrl("st", "challenge", " ")).doesNotContain("login_hint");
    }

    @Test
    void authorize_url_fails_when_not_configured() {
        var unconfigured = new GmailOAuthClient(new GmailProperties(null, null, "http://cb", null, 365), RestClient.builder());

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.authorizeUrl("st", "challenge", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void exchange_code_posts_code_verifier_and_redirect_uri() {
        server.expect(requestTo(TOKEN_URL)).andExpect(method(POST))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "authorization_code",
                        "code", "4/code",
                        "code_verifier", "verifier",
                        "client_id", "cid.apps.googleusercontent.com",
                        "client_secret", "csecret",
                        "redirect_uri", "http://localhost:8080/api/mail/accounts/callback")))
                .andRespond(withSuccess("""
                        {"access_token":"ya29.at","expires_in":3599,"refresh_token":"1//rt","scope":"openid","token_type":"Bearer","id_token":"x"}
                        """, MediaType.APPLICATION_JSON));

        OAuthTokens tokens = client.exchangeCode("4/code", "verifier");

        assertThat(tokens.accessToken()).isEqualTo("ya29.at");
        assertThat(tokens.refreshToken()).isEqualTo("1//rt");
        assertThat(tokens.expiresAt()).isBetween(Instant.now().plusSeconds(3500), Instant.now().plusSeconds(3600));
        server.verify();
    }

    @Test
    void refresh_keeps_the_old_refresh_token_because_google_sends_none() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().formDataContains(Map.of("grant_type", "refresh_token", "refresh_token", "1//rt")))
                .andRespond(withSuccess("""
                        {"access_token":"ya29.new","expires_in":3599,"token_type":"Bearer"}
                        """, MediaType.APPLICATION_JSON));

        OAuthTokens tokens = client.refresh("1//rt");

        assertThat(tokens.accessToken()).isEqualTo("ya29.new");
        assertThat(tokens.refreshToken()).isNull();
    }

    @Test
    void revoked_refresh_token_fails_the_refresh() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error":"invalid_grant","error_description":"Token has been expired or revoked."}
                        """));

        assertThatThrownBy(() -> client.refresh("1//rt")).isInstanceOf(HttpClientErrorException.class);
    }
}
