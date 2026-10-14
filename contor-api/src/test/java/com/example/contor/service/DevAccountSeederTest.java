package com.example.contor.service;

import com.example.contor.config.DevAccountProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.validation.PasswordValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevAccountSeederTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordValidator passwordValidator;

    private DevAccountSeeder seeder(String username, String password) {
        return new DevAccountSeeder(new DevAccountProperties(username, "dev@outlook.com", password),
                appUserRepository, passwordEncoder, passwordValidator);
    }

    @Test
    void run_CreatesOwnerWhenMissing() {
        when(appUserRepository.findByUsername(new Username("dev_user"))).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Dev-Password-12!")).thenReturn("argon-hash");

        seeder("dev_user", "Dev-Password-12!").run(null);

        verify(passwordValidator).validate("Dev-Password-12!");
        verify(appUserRepository).saveAndFlush(argThat(u -> u.getUsername().value().equals("dev_user")
                && u.getRole() == UserRole.OWNER
                && u.getEmail().value().equals("dev@outlook.com")
                && u.getPasswordHash().equals("argon-hash")));
    }

    @Test
    void run_ResetsPasswordAndLockoutWhenExisting() {
        var user = AppUser.builder().id(new AppUser.AppUserId(1L)).username(new Username("dev_user"))
                .email(new Email("dev@outlook.com")).passwordHash("old-hash").role(UserRole.OWNER)
                .failedLoginAttempts(4).lockoutCount(2).lockedUntil(Instant.now().plusSeconds(600)).build();
        when(appUserRepository.findByUsername(new Username("dev_user"))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Dev-Password-12!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("Dev-Password-12!")).thenReturn("new-hash");

        seeder("dev_user", "Dev-Password-12!").run(null);

        assertEquals("new-hash", user.getPasswordHash());
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(0, user.getLockoutCount());
        assertNull(user.getLockedUntil());
        verify(appUserRepository).saveAndFlush(user);
    }

    @Test
    void run_SkipsWhenNotConfigured() {
        seeder("", null).run(null);

        verify(appUserRepository, never()).saveAndFlush(any());
        verifyNoInteractions(passwordValidator);
    }
}
