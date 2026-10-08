package com.example.contor.service;

import com.example.contor.config.DevAccountProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.validation.PasswordValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dev profile only: keeps the configured dev login (DEV_ACCOUNT_* in .env) ready to sign in. Creates it as OWNER when
 * missing, otherwise resets its password and lockout. Runs before the setup-token banner (ApplicationReadyEvent).
 */
@Slf4j
@Profile("dev")
@RequiredArgsConstructor
@Component
public class DevAccountSeeder implements ApplicationRunner {

    private final DevAccountProperties properties;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.info("No dev account configured (DEV_ACCOUNT_USERNAME / DEV_ACCOUNT_PASSWORD)");
            return;
        }
        Username username = new Username(properties.username());
        passwordValidator.validate(properties.password());

        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            appUserRepository.saveAndFlush(AppUser.builder()
                    .username(username)
                    .email(new Email(properties.email()))
                    .passwordHash(passwordEncoder.encode(properties.password()))
                    .role(UserRole.OWNER)
                    .build());
            log.info("Dev account '{}' created", username.value());
            return;
        }
        if (!passwordEncoder.matches(properties.password(), user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(properties.password()));
        }
        user.setFailedLoginAttempts(0);
        user.setLockoutCount(0);
        user.setLockedUntil(null);
        appUserRepository.saveAndFlush(user);
        log.info("Dev account '{}' ready", username.value());
    }
}
