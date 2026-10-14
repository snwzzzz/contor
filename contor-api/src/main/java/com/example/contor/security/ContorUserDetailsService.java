package com.example.contor.security;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Loads users for password verification. Locked accounts are reported locked, which the login maps to the generic error. */
@RequiredArgsConstructor
@Component
public class ContorUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final Clock clock;

    @Override
    public UserDetails loadUserByUsername(String username) {
        Username validUsername;
        try {
            validUsername = new Username(username);
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("User not found");
        }
        AppUser user = appUserRepository.findByUsername(validUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(user.getUsername().value())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .accountLocked(user.isLockedAt(clock.instant()))
                .build();
    }
}
