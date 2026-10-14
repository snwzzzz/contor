package com.example.contor.service;

import com.example.contor.config.DemoMailboxProperties;
import com.example.contor.config.DevAccountProperties;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Dev profile only: with {@code DEMO_MAILBOX_ENABLED=true}, imports the fictional business mailbox into the dev login
 * once the app is ready, i.e. after {@link DevAccountSeeder} and {@link SystemDataSeeder} (the relation types need the
 * seeded People and Organizations). Runs on every start but imports only once per login.
 */
@Slf4j
@Profile("dev")
@RequiredArgsConstructor
@Component
public class DemoMailboxImporter {

    private final DemoMailboxProperties properties;
    private final DevAccountProperties devAccount;
    private final AppUserRepository appUserRepository;
    private final DemoMailboxService demoMailboxService;

    @EventListener(ApplicationReadyEvent.class)
    public void importDemoMailbox() {
        if (!properties.enabled()) {
            return;
        }
        if (!devAccount.isConfigured()) {
            log.warn("Demo mailbox enabled, but no dev account configured (DEV_ACCOUNT_USERNAME / DEV_ACCOUNT_PASSWORD)");
            return;
        }
        appUserRepository.findByUsername(new Username(devAccount.username())).ifPresentOrElse(
                user -> {
                    Path path = Path.of(properties.path());
                    try {
                        int imported = demoMailboxService.importMailbox(user, path, properties.shiftToToday());
                        if (imported == 0) {
                            log.info("Demo mailbox already imported for '{}'", devAccount.username());
                        }
                    } catch (RuntimeException e) {
                        // a dev convenience must never stop the backend from starting
                        log.warn("Demo mailbox not imported from {}: {} (set DEMO_MAILBOX_PATH)", path.toAbsolutePath(), e.getMessage());
                    }
                },
                () -> log.warn("Demo mailbox enabled, but the dev account '{}' does not exist", devAccount.username()));
    }
}
