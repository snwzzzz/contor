package com.example.contor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;

import java.time.Clock;

/** Beans the security configuration needs that the MVC slice does not provide (JDBC sessions are not loaded there). */
@TestConfiguration(proxyBeanMethods = false)
public class ControllerTestConfiguration {

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
