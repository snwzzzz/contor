package com.example.contor.security;

import com.example.contor.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** First-run setup is pending exactly while no user exists. Checked only for /api/setup requests. */
@RequiredArgsConstructor
@Component
public class SetupState {

    private final AppUserRepository appUserRepository;

    public boolean isPending() {
        return appUserRepository.count() == 0;
    }
}
