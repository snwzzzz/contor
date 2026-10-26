package com.example.contor.mailprovider;

import com.example.contor.config.MsGraphProperties;
import com.example.contor.domain.MailProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MicrosoftGraphOAuthClientTest {

    private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";

    private MockRestServiceServer server;
    private MicrosoftGraphOAuthClient client;

    @BeforeEach
    void setUp() {
        var properties = new MsGraphProperties("test-client-id", "test-secret", "common",
                "http://localhost:8080/api/mail/accounts/callback", "Mail.Read,User.Read,offline_access");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MicrosoftGraphOAuthClient(properties, builder);
    }

    @Test
    void is_the_microsoft_graph_provider() {
        assertThat(client.provider()).isEqualTo(MailProvider.MICROSOFT_GRAPH);
        assertThat(client.displayName()).isEqualTo("Outlook");
        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    void authorize_url_carries_client_state_pkce_and_login_hint() {
        String url = client.authorizeUrl("st", "challenge", "user@outlook.com");

        assertThat(url).startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?");
        assertThat(url).contains("client_id=test-client-id", "state=st", "code_challenge=challenge",
                "code_challenge_method=S256", "login_hint=user%40outlook.com", "response_type=code");
    }

    @Test
    void authorize_url_sends_space_separated_scopes_so_offline_access_is_granted() {
        assertThat(client.authorizeUrl("st", "challenge", null)).contains("scope=Mail.Read+User.Read+offline_access");
    }

    @Test
    void authorize_url_fails_when_not_configured() {
        var unconfigured = new MicrosoftGraphOAuthClient(
                new MsGraphProperties(null, null, "common", "http://localhost:8080/cb", null), RestClient.builder());

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.authorizeUrl("st", "challenge", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void exchange_code_sends_the_pkce_verifier() {
        server.expect(requestTo(TOKEN_URL)).andExpect(method(POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "authorization_code", "code", "the-code", "code_verifier", "the-verifier")))
                .andRespond(withSuccess("""
                        {"access_token":"at","refresh_token":"rt","expires_in":3600,"token_type":"Bearer"}
                        """, MediaType.APPLICATION_JSON));

        OAuthTokens tokens = client.exchangeCode("the-code", "the-verifier");

        assertThat(tokens.accessToken()).isEqualTo("at");
        assertThat(tokens.refreshToken()).isEqualTo("rt");
        assertThat(tokens.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(3000));
        server.verify();
    }

    @Test
    void refresh_uses_the_refresh_token_grant() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "refresh_token", "refresh_token", "old")))
                .andRespond(withSuccess("""
                        {"access_token":"new","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));

        OAuthTokens tokens = client.refresh("old");

        assertThat(tokens.accessToken()).isEqualTo("new");
        assertThat(tokens.refreshToken()).isNull();
    }
}
