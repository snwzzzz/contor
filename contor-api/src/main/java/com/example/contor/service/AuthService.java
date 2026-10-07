package com.example.contor.service;

import com.example.contor.api.model.ChangePasswordCommand;
import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.LoginCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Username;
import com.example.contor.exception.AppUserNotFoundException;
import com.example.contor.exception.InvalidCredentialsException;
import com.example.contor.exception.TooManyRequestsException;
import com.example.contor.mapper.AppUserMapper;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.security.LoginRateLimiter;
import com.example.contor.validation.PasswordValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * JSON login/logout on top of Spring Security. Login verifies credentials through the {@link AuthenticationManager},
 * then applies the session strategy (limit, id rotation, CSRF refresh) and stores the context in the session.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final LoginAttemptService loginAttemptService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditService auditService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SessionRegistry sessionRegistry;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    /** Failed attempts must be persisted although the login fails, hence no rollback for invalid credentials. */
    @Transactional(dontRollbackOn = InvalidCredentialsException.class)
    public CurrentUserDto login(LoginCommand command, HttpServletRequest request, HttpServletResponse response) {
        if (!loginRateLimiter.tryAcquire(request.getRemoteAddr())) {
            throw new TooManyRequestsException("Too many login attempts. Try again later.");
        }
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(command.getUsername(), command.getPassword()));
        } catch (AuthenticationException e) {
            Long userId = loginAttemptService.recordFailure(command.getUsername()).orElse(null);
            auditService.record(AuditEvent.user(AuditAction.AUTH_LOGIN_FAILURE, userId));
            log.info("Login failed ({})", e.getClass().getSimpleName());
            throw new InvalidCredentialsException();
        }

        AppUser user = appUserRepository.findByUsername(new Username(command.getUsername()))
                .orElseThrow(InvalidCredentialsException::new);
        loginAttemptService.recordSuccess(user);

        AuthenticatedUser principal = AuthenticatedUser.of(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        auditService.record(AuditEvent.user(AuditAction.AUTH_LOGIN_SUCCESS, principal.userId()).entity("app_user", principal.userId()));
        return AppUserMapper.toDto(user);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser principal = currentPrincipal();
        auditService.record(AuditEvent.user(AuditAction.AUTH_LOGOUT, principal.userId()).entity("app_user", principal.userId()));
        var logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.setSecurityContextRepository(securityContextRepository);
        logoutHandler.logout(request, response, authentication);
    }

    @Transactional
    public CurrentUserDto getCurrentUser() {
        return AppUserMapper.toDto(currentUser());
    }

    /** Verifies the current password, applies the password rules and ends every other session of the user. */
    @Transactional
    public void changePassword(ChangePasswordCommand command, HttpServletRequest request) {
        AppUser user = currentUser();
        if (!passwordEncoder.matches(command.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        passwordValidator.validate(command.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(command.getNewPassword()));
        appUserRepository.saveAndFlush(user);

        HttpSession currentSession = request.getSession(false);
        String currentSessionId = currentSession != null ? currentSession.getId() : null;
        for (SessionInformation session : sessionRegistry.getAllSessions(currentPrincipal(), false)) {
            if (!session.getSessionId().equals(currentSessionId)) {
                session.expireNow();
            }
        }
        auditService.record(AuditEvent.user(AuditAction.AUTH_PASSWORD_CHANGED, user.getId().id()).entity("app_user", user.getId().id()));
    }

    private AuthenticatedUser currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new IllegalStateException("No authenticated user");
        }
        return principal;
    }

    private AppUser currentUser() {
        Long userId = currentPrincipal().userId();
        return appUserRepository.findById(new AppUser.AppUserId(userId))
                .orElseThrow(() -> new AppUserNotFoundException(userId));
    }
}
