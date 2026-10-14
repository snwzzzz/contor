package com.example.contor.service;

import com.example.contor.MutableClock;
import com.example.contor.config.SecurityProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuditService auditService;

    private final MutableClock clock = new MutableClock(NOW);

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setup() {
        var lockout = new SecurityProperties.Lockout(5, Duration.ofMinutes(15), Duration.ofHours(24));
        loginAttemptService = new LoginAttemptService(appUserRepository, auditService, lockout, clock);
    }

    private AppUser user() {
        return AppUser.builder().id(new AppUser.AppUserId(1L)).username(new Username("owner"))
                .email(new Email("owner@example.com")).passwordHash("hash").role(UserRole.OWNER).build();
    }

    @Test
    void recordFailure_CountsFailedAttempts() {
        AppUser user = user();
        when(appUserRepository.findByUsername(new Username("owner"))).thenReturn(Optional.of(user));

        loginAttemptService.recordFailure("owner");

        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(appUserRepository).saveAndFlush(user);
        verify(auditService, never()).record(argThat(e -> e.action() == AuditAction.AUTH_LOCKOUT));
    }

    @Test
    void recordFailure_LocksAfterFifthFailure() {
        AppUser user = user();
        user.setFailedLoginAttempts(4);
        when(appUserRepository.findByUsername(new Username("owner"))).thenReturn(Optional.of(user));

        loginAttemptService.recordFailure("owner");

        assertEquals(NOW.plus(Duration.ofMinutes(15)), user.getLockedUntil());
        assertEquals(1, user.getLockoutCount());
        assertEquals(0, user.getFailedLoginAttempts());
        verify(auditService).record(argThat(e -> e.action() == AuditAction.AUTH_LOCKOUT && e.actorUserId() == 1L));
    }

    @Test
    void recordFailure_LockDurationDoublesOnRepeatedLockouts() {
        AppUser user = user();
        user.setFailedLoginAttempts(4);
        user.setLockoutCount(2);
        when(appUserRepository.findByUsername(new Username("owner"))).thenReturn(Optional.of(user));

        loginAttemptService.recordFailure("owner");

        assertEquals(NOW.plus(Duration.ofMinutes(60)), user.getLockedUntil());
        assertEquals(3, user.getLockoutCount());
    }

    @Test
    void recordFailure_LockDurationIsCapped() {
        AppUser user = user();
        user.setFailedLoginAttempts(4);
        user.setLockoutCount(20);
        when(appUserRepository.findByUsername(new Username("owner"))).thenReturn(Optional.of(user));

        loginAttemptService.recordFailure("owner");

        assertEquals(NOW.plus(Duration.ofHours(24)), user.getLockedUntil());
    }

    @Test
    void recordFailure_UnknownUserIgnored() {
        when(appUserRepository.findByUsername(any())).thenReturn(Optional.empty());

        loginAttemptService.recordFailure("ghost");

        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void recordFailure_InvalidUsernameIgnored() {
        loginAttemptService.recordFailure("x");

        verifyNoInteractions(appUserRepository);
    }

    @Test
    void recordSuccess_ResetsCountersAndStampsLastLogin() {
        AppUser user = user();
        user.setFailedLoginAttempts(3);
        user.setLockoutCount(2);
        user.setLockedUntil(NOW.minusSeconds(1));

        loginAttemptService.recordSuccess(user);

        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(0, user.getLockoutCount());
        assertNull(user.getLockedUntil());
        assertEquals(NOW, user.getLastLoginAt());
        verify(appUserRepository).saveAndFlush(user);
    }
}
