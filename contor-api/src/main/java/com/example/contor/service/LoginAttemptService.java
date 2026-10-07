package com.example.contor.service;

import com.example.contor.config.SecurityProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Per-username brute-force protection: counts failures, locks with exponential back-off, resets on success. */
@Service
public class LoginAttemptService {

    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final SecurityProperties.Lockout lockout;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(AppUserRepository appUserRepository, AuditService auditService,
                               SecurityProperties properties, Clock clock) {
        this(appUserRepository, auditService, properties.lockout(), clock);
    }

    public LoginAttemptService(AppUserRepository appUserRepository, AuditService auditService,
                               SecurityProperties.Lockout lockout, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.lockout = lockout;
        this.clock = clock;
    }

    /** Returns the id of the existing user the failure was counted for, if any. Failures while locked are not counted. */
    @Transactional
    public Optional<Long> recordFailure(String username) {
        Optional<AppUser> found;
        try {
            found = appUserRepository.findByUsername(new Username(username));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = found.get();
        Instant now = clock.instant();
        if (!user.isLockedAt(now)) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= lockout.maxFailedAttempts()) {
                lock(user, now);
            }
            appUserRepository.saveAndFlush(user);
        }
        return Optional.of(user.getId().id());
    }

    @Transactional
    public void recordSuccess(AppUser user) {
        user.setFailedLoginAttempts(0);
        user.setLockoutCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(clock.instant());
        appUserRepository.saveAndFlush(user);
    }

    private void lock(AppUser user, Instant now) {
        int lockouts = user.getLockoutCount() + 1;
        Duration duration = lockDuration(lockouts);
        user.setLockoutCount(lockouts);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(now.plus(duration));
        auditService.record(AuditEvent.user(AuditAction.AUTH_LOCKOUT, user.getId().id())
                .entity("app_user", user.getId().id())
                .after(Map.of("lockedForSeconds", duration.toSeconds(), "lockoutCount", lockouts)));
    }

    private Duration lockDuration(int lockouts) {
        int doublings = Math.min(lockouts - 1, 30);
        Duration duration = lockout.baseDuration().multipliedBy(1L << doublings);
        return duration.compareTo(lockout.maxDuration()) > 0 ? lockout.maxDuration() : duration;
    }
}
