package com.example.contor.integration;

import com.example.contor.PostgresTestcontainersConfiguration;
import com.example.contor.security.SetupTokenHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security requirements end to end over real HTTP (session cookies, CSRF, lockout, setup, session rotation and limits).
 * Encrypted columns at rest are covered by AppUserRepositoryTest and MailAccountRepositoryTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.login-rate-limit.max-attempts=1000")
@Import(PostgresTestcontainersConfiguration.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SecurityIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-7-Battery";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SetupTokenHolder setupTokenHolder;

    @Autowired
    private Environment environment;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table \"app_user\", spring_session cascade");
    }

    private ApiClient client() {
        return new ApiClient(port);
    }

    private ApiClient.Response setupOwner(ApiClient client) {
        var token = setupTokenHolder.issue();
        return client.post("/api/setup", """
                {"token":"%s","username":"owner","email":"owner@example.com","password":"%s"}""".formatted(token, PASSWORD));
    }

    private ApiClient.Response login(ApiClient client, String password) {
        return client.post("/api/auth/login", """
                {"username":"owner","password":"%s"}""".formatted(password));
    }

    private ApiClient loggedInOwner() {
        var client = client();
        login(client, PASSWORD);
        return client;
    }

    private long auditCount(String action) {
        return jdbcTemplate.queryForObject("select count(*) from \"audit_log\" where action = ?", Long.class, action);
    }

    @Test
    void unauthenticated_access_to_a_protected_endpoint_returns_401() {
        var response = client().get("/api/auth/me");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body()).contains("\"message\":\"Authentication required\"").contains("\"status\":401");
    }

    @Test
    void mutating_request_without_csrf_token_returns_403() {
        setupOwner(client());
        var client = loggedInOwner();

        var response = client.postWithoutCsrf("/api/auth/logout", null);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body()).contains("Invalid or missing CSRF token");
        assertThat(client.get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void account_is_locked_after_five_failed_logins() {
        setupOwner(client());
        var client = client();

        for (int i = 0; i < 5; i++) {
            assertThat(login(client, "Wrong-Password-1!").status()).isEqualTo(401);
        }
        var withCorrectPassword = login(client, PASSWORD);

        assertThat(withCorrectPassword.status()).isEqualTo(401);
        assertThat(withCorrectPassword.body()).contains("Invalid username or password");
        assertThat(jdbcTemplate.queryForObject("select locked_until is not null from \"app_user\"", Boolean.class)).isTrue();
        assertThat(auditCount("AUTH_LOGIN_FAILURE")).isEqualTo(6);
        assertThat(auditCount("AUTH_LOCKOUT")).isEqualTo(1);
    }

    @Test
    void unknown_user_wrong_password_and_locked_account_share_one_message() {
        setupOwner(client());
        var unknown = client().post("/api/auth/login", "{\"username\":\"ghost\",\"password\":\"whatever\"}");
        var wrong = login(client(), "Wrong-Password-1!");

        assertThat(unknown.status()).isEqualTo(wrong.status()).isEqualTo(401);
        assertThat(unknown.body()).isEqualTo(wrong.body());
    }

    @Test
    void setup_endpoint_is_unavailable_once_a_user_exists() {
        assertThat(client().get("/api/setup").body()).contains("\"setupRequired\":true");
        assertThat(setupOwner(client()).status()).isEqualTo(201);

        var anonymousRetry = setupOwner(client());
        var anonymousStatus = client().get("/api/setup");
        var ownerRetry = setupOwner(loggedInOwner());

        assertThat(anonymousRetry.status()).isEqualTo(401);
        assertThat(anonymousStatus.status()).isEqualTo(401);
        assertThat(ownerRetry.status()).isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject("select count(*) from \"app_user\"", Long.class)).isEqualTo(1);
    }

    @Test
    void setup_rejects_a_wrong_token_and_weak_passwords_with_all_violations() {
        setupTokenHolder.issue();
        var wrongToken = client().post("/api/setup", """
                {"token":"wrong","username":"owner","email":"owner@example.com","password":"%s"}""".formatted(PASSWORD));
        var token = setupTokenHolder.issue();
        var weak = client().post("/api/setup", """
                {"token":"%s","username":"owner","email":"owner@example.com","password":"short"}""".formatted(token));

        assertThat(wrongToken.status()).isEqualTo(403);
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.body()).contains("at least 12 characters").contains("uppercase").contains("digit").contains("special character");
    }

    @Test
    void session_id_changes_on_login_and_the_old_session_is_dead() {
        setupOwner(client());
        var client = client();
        assertThat(login(client, PASSWORD).status()).isEqualTo(200);
        var firstSession = client.cookie("CONTOR_SESSION").orElseThrow();

        assertThat(login(client, PASSWORD).status()).isEqualTo(200);
        var secondSession = client.cookie("CONTOR_SESSION").orElseThrow();

        assertThat(secondSession).isNotEqualTo(firstSession);
        var attacker = client();
        attacker.setCookie("CONTOR_SESSION", firstSession);
        assertThat(attacker.get("/api/auth/me").status()).isEqualTo(401);
        assertThat(client.get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void session_cookie_is_http_only_and_same_site_strict_and_csrf_cookie_is_readable() {
        setupOwner(client());
        var response = login(client(), PASSWORD);

        assertThat(response.setCookies()).anySatisfy(c -> assertThat(c)
                .startsWith("CONTOR_SESSION=").containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Strict"));
        assertThat(response.setCookies()).anySatisfy(c -> assertThat(c)
                .startsWith("XSRF-TOKEN=").doesNotContainIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Strict"));
    }

    @Test
    void csrf_endpoint_sets_a_readable_same_site_strict_cookie() {
        var response = client().get("/api/auth/csrf");

        assertThat(response.status()).isEqualTo(204);
        assertThat(response.setCookies()).singleElement().satisfies(c -> assertThat(c)
                .startsWith("XSRF-TOKEN=").doesNotContainIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Strict"));
    }

    @Test
    void full_flow_setup_login_me_logout() {
        assertThat(setupOwner(client()).status()).isEqualTo(201);
        var client = client();

        var login = login(client, PASSWORD);
        var me = client.get("/api/auth/me");
        var logout = client.post("/api/auth/logout", null);
        var meAfterLogout = client.get("/api/auth/me");

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body()).contains("\"username\":\"owner\"").contains("\"role\":\"OWNER\"").doesNotContain("password");
        assertThat(me.status()).isEqualTo(200);
        assertThat(logout.status()).isEqualTo(204);
        assertThat(meAfterLogout.status()).isEqualTo(401);
        assertThat(auditCount("SETUP_COMPLETED")).isEqualTo(1);
        assertThat(auditCount("AUTH_LOGIN_SUCCESS")).isEqualTo(1);
        assertThat(auditCount("AUTH_LOGOUT")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select password_hash from \"app_user\"", String.class)).startsWith("$argon2id$");
    }

    @Test
    void at_most_three_sessions_per_user_and_the_oldest_is_expired() {
        setupOwner(client());
        var first = loggedInOwner();
        var second = loggedInOwner();
        var third = loggedInOwner();

        var fourth = loggedInOwner();

        assertThat(first.get("/api/auth/me").status()).isEqualTo(401);
        assertThat(second.get("/api/auth/me").status()).isEqualTo(200);
        assertThat(third.get("/api/auth/me").status()).isEqualTo(200);
        assertThat(fourth.get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void changing_the_password_ends_all_other_sessions() {
        setupOwner(client());
        var current = loggedInOwner();
        var other = loggedInOwner();

        var change = current.put("/api/auth/password", """
                {"currentPassword":"%s","newPassword":"Another-Strong-Pass-8"}""".formatted(PASSWORD));

        assertThat(change.status()).isEqualTo(204);
        assertThat(current.get("/api/auth/me").status()).isEqualTo(200);
        assertThat(other.get("/api/auth/me").status()).isEqualTo(401);
        assertThat(login(client(), "Another-Strong-Pass-8").status()).isEqualTo(200);
        assertThat(auditCount("AUTH_PASSWORD_CHANGED")).isEqualTo(1);
    }

    @Test
    void actuator_health_is_public_without_details_and_other_actuators_are_hidden() {
        var health = client().get("/actuator/health");

        assertThat(health.status()).isEqualTo(200);
        assertThat(health.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(client().get("/actuator/env").status()).isIn(401, 404);
    }

    @Test
    void client_ip_forwarded_by_the_local_proxy_is_recorded() {
        setupOwner(client());

        login(client().withHeader("X-Forwarded-For", "203.0.113.9"), "Wrong-Password-1!");

        assertThat(jdbcTemplate.queryForObject(
                "select ip_address from \"audit_log\" where action = 'AUTH_LOGIN_FAILURE'", String.class)).isEqualTo("203.0.113.9");
    }

    @Test
    void only_loopback_is_a_trusted_proxy() {
        var internalProxies = Pattern.compile(environment.getRequiredProperty("server.tomcat.remoteip.internal-proxies"));

        assertThat(internalProxies.matcher("127.0.0.1").matches()).isTrue();
        assertThat(internalProxies.matcher("0:0:0:0:0:0:0:1").matches()).isTrue();
        assertThat(internalProxies.matcher("192.168.1.5").matches()).isFalse();
        assertThat(internalProxies.matcher("10.0.0.2").matches()).isFalse();
        assertThat(internalProxies.matcher("172.16.0.3").matches()).isFalse();
    }

    @Test
    void api_responses_carry_a_strict_content_security_policy() {
        var response = client().get("/api/auth/csrf");

        assertThat(response.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(csp ->
                assertThat(csp).contains("default-src 'none'").contains("frame-ancestors 'none'"));
    }
}
