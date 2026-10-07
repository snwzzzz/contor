package com.example.contor.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** On startup without any user, issues the one-time setup token and prints it to the log (the only place it appears). */
@Slf4j
@RequiredArgsConstructor
@Component
public class SetupTokenInitializer {

    private final SetupState setupState;
    private final SetupTokenHolder setupTokenHolder;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!setupState.isPending()) {
            return;
        }
        String token = setupTokenHolder.issue();
        log.warn("""

                ==================================================================
                 contor first-run setup: no user exists yet.
                 Open the web app and create the owner account with this
                 one-time setup token (valid until used or the app restarts):

                   {}
                ==================================================================""", token);
    }
}
