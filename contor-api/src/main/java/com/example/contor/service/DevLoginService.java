package com.example.contor.service;

import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.DevLoginDto;
import com.example.contor.config.DevAccountProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Username;
import com.example.contor.exception.DevLoginUnavailableException;
import com.example.contor.mapper.AppUserMapper;
import com.example.contor.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Dev profile only: passwordless sign-in as the configured dev account (the one {@link DevAccountSeeder} keeps
 * ready), so the local web app logs in by itself. Refused for requests that do not come from this machine.
 */
@Slf4j
@Profile("dev")
@RequiredArgsConstructor
@Service
public class DevLoginService {

    private final DevAccountProperties properties;
    private final AppUserRepository appUserRepository;
    private final AuthService authService;
    private final AuditService auditService;

    @Transactional
    public DevLoginDto getDevLogin(HttpServletRequest request) {
        requireAvailable(request);
        return new DevLoginDto(properties.username());
    }

    @Transactional
    public CurrentUserDto signIn(HttpServletRequest request, HttpServletResponse response) {
        requireAvailable(request);
        AppUser user = appUserRepository.findByUsername(new Username(properties.username()))
                .orElseThrow(DevLoginUnavailableException::new);
        authService.startSession(user, request, response);
        auditService.record(AuditEvent.user(AuditAction.AUTH_DEV_LOGIN, user.getId().id()).entity("app_user", user.getId().id()));
        log.info("Dev sign-in as '{}'", user.getUsername().value());
        return AppUserMapper.toDto(user);
    }

    private void requireAvailable(HttpServletRequest request) {
        if (!properties.isConfigured()) {
            throw new DevLoginUnavailableException();
        }
        if (!isLoopback(request.getRemoteAddr())) {
            throw new AccessDeniedException("Dev sign-in is only available on this machine");
        }
    }

    /** The remote address is already resolved by Tomcat's RemoteIpValve (only loopback proxies are trusted). */
    private static boolean isLoopback(String address) {
        try {
            return address != null && InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
