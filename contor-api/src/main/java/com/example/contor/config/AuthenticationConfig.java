package com.example.contor.config;

import com.example.contor.security.ContorUserDetailsService;
import com.example.contor.security.LoginRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AuthenticationConfig {

    /** Argon2id with OWASP parameters: 19 MiB memory, 2 iterations, parallelism 1, 16-byte salt, 32-byte hash. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    }

    @Bean
    public AuthenticationManager authenticationManager(ContorUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public LoginRateLimiter loginRateLimiter(SecurityProperties properties, Clock clock) {
        return new LoginRateLimiter(properties.loginRateLimit(), clock);
    }
}
