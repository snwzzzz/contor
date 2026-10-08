package com.example.contor.config;

import com.example.contor.security.AbsoluteSessionTimeoutFilter;
import com.example.contor.security.JsonSecurityErrorHandler;
import com.example.contor.security.SetupState;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;

/**
 * The single web security configuration. Deny by default; session cookies (Spring Session JDBC); CSRF via readable
 * XSRF-TOKEN cookie + X-XSRF-TOKEN header; no CORS (the Next.js server proxies /api/*); JSON 401/403 bodies.
 * Login happens in {@code AuthService} (JSON endpoint), which applies {@link #sessionAuthenticationStrategy}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
// Class-based proxies: controllers implement the generated API interfaces; an interface proxy would hide @RestController from Spring MVC.
@EnableMethodSecurity(proxyTargetClass = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    static final String SWAGGER_CONTENT_SECURITY_POLICY =
            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(SecurityProperties properties) {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(properties.secureCookies()).path("/"));
        return repository;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** Applied on every successful login: session limit, session id rotation, registration, fresh CSRF token. */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry,
                                                                       CookieCsrfTokenRepository csrfTokenRepository,
                                                                       SecurityProperties properties) {
        var concurrency = new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrency.setMaximumSessions(properties.maxSessionsPerUser());
        concurrency.setExceptionIfMaximumExceeded(false);
        var csrf = new CsrfAuthenticationStrategy(csrfTokenRepository);
        var eagerCsrfHandler = new XorCsrfTokenRequestAttributeHandler();
        eagerCsrfHandler.setCsrfRequestAttributeName(null);
        csrf.setRequestHandler(eagerCsrfHandler);
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrency,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry),
                csrf));
    }

    @Bean
    public JsonSecurityErrorHandler jsonSecurityErrorHandler(ObjectMapper objectMapper) {
        return new JsonSecurityErrorHandler(objectMapper);
    }

    /** Swagger UI renders the static contract; dev profile only, still authenticated, relaxed CSP for its assets. */
    @Bean
    @Order(1)
    @Profile("dev")
    public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http,
                                                          SecurityContextRepository securityContextRepository,
                                                          JsonSecurityErrorHandler errorHandler) throws Exception {
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/openapi.yaml")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errorHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(SWAGGER_CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      CookieCsrfTokenRepository csrfTokenRepository,
                                                      SecurityContextRepository securityContextRepository,
                                                      SessionRegistry sessionRegistry,
                                                      JsonSecurityErrorHandler errorHandler,
                                                      SetupState setupState,
                                                      SecurityProperties properties,
                                                      Clock clock,
                                                      Environment environment) throws Exception {
        // Passwordless dev sign-in (DevController, loopback only) is public only with the dev profile.
        AuthorizationManager<RequestAuthorizationContext> devLogin = environment.matchesProfiles("dev")
                ? (authentication, context) -> new AuthorizationDecision(true)
                : AuthenticatedAuthorizationManager.authenticated();
        AuthorizationManager<RequestAuthorizationContext> setupPending =
                (authentication, context) -> new AuthorizationDecision(setupState.isPending());

        http
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                        .maximumSessions(properties.maxSessionsPerUser())
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(errorHandler)))
                .addFilterBefore(new AbsoluteSessionTimeoutFilter(properties.sessionAbsoluteTimeout(), clock),
                        SecurityContextHolderFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(API_CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/setup").access(AuthorizationManagers.anyOf(
                                setupPending, AuthenticatedAuthorizationManager.authenticated()))
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/mail/accounts/callback").permitAll()
                        .requestMatchers("/api/auth/dev-login").access(devLogin)
                        .anyRequest().authenticated());
        return http.build();
    }
}
