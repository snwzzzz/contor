package com.example.contor.service;

import com.example.contor.api.model.ChangePasswordCommand;
import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.LoginCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.exception.InvalidCredentialsException;
import com.example.contor.exception.PasswordValidationException;
import com.example.contor.exception.TooManyRequestsException;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.security.LoginRateLimiter;
import com.example.contor.validation.PasswordValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @Mock
    private AuditService auditService;

    @Mock
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordValidator passwordValidator;

    @InjectMocks
    private AuthService authService;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private AppUser user() {
        return AppUser.builder().id(new AppUser.AppUserId(1L)).username(new Username("owner"))
                .email(new Email("owner@example.com")).passwordHash("hash").role(UserRole.OWNER).build();
    }

    private void loggedInAs(AppUser user) {
        var principal = AuthenticatedUser.of(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @Test
    void login() {
        request.setRemoteAddr("10.0.0.1");
        when(loginRateLimiter.tryAcquire("10.0.0.1")).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findByUsername(new Username("owner"))).thenReturn(Optional.of(user()));

        CurrentUserDto result = authService.login(new LoginCommand("owner", "secret"), request, response);

        assertEquals(1L, result.getId());
        verify(loginAttemptService).recordSuccess(any(AppUser.class));
        verify(sessionAuthenticationStrategy).onAuthentication(argThat(a -> a.getPrincipal() instanceof AuthenticatedUser), eq(request), eq(response));
        verify(securityContextRepository).saveContext(argThat(c -> c.getAuthentication().isAuthenticated()), eq(request), eq(response));
        verify(auditService).record(argThat(e -> e.action() == AuditAction.AUTH_LOGIN_SUCCESS));
        assertNull(((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getPassword());
    }

    @Test
    void login_BadCredentialsRecordedWithGenericMessage() {
        when(loginRateLimiter.tryAcquire(any())).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        var exception = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginCommand("owner", "wrong"), request, response));

        assertEquals("Invalid username or password", exception.getMessage());
        verify(loginAttemptService).recordFailure("owner");
        verify(auditService).record(argThat(e -> e.action() == AuditAction.AUTH_LOGIN_FAILURE));
        verify(sessionAuthenticationStrategy, never()).onAuthentication(any(), any(), any());
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    void login_LockedAccountGetsSameMessage() {
        when(loginRateLimiter.tryAcquire(any())).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("User account is locked"));

        var exception = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginCommand("owner", "secret"), request, response));

        assertEquals("Invalid username or password", exception.getMessage());
    }

    @Test
    void login_RateLimited() {
        when(loginRateLimiter.tryAcquire(any())).thenReturn(false);

        assertThrows(TooManyRequestsException.class,
                () -> authService.login(new LoginCommand("owner", "secret"), request, response));
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void getCurrentUser() {
        AppUser user = user();
        loggedInAs(user);
        when(appUserRepository.findById(new AppUser.AppUserId(1L))).thenReturn(Optional.of(user));

        assertEquals("owner", authService.getCurrentUser().getUsername());
    }

    @Test
    void changePassword() {
        AppUser user = user();
        loggedInAs(user);
        request.getSession();
        var other = new SessionInformation("owner", "other-session", new Date());
        var current = new SessionInformation("owner", request.getSession().getId(), new Date());
        when(appUserRepository.findById(new AppUser.AppUserId(1L))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.encode("Correct-Horse-7-Battery")).thenReturn("new-hash");
        when(sessionRegistry.getAllSessions(any(), eq(false))).thenReturn(List.of(other, current));

        authService.changePassword(new ChangePasswordCommand("old", "Correct-Horse-7-Battery"), request);

        assertEquals("new-hash", user.getPasswordHash());
        assertTrue(other.isExpired());
        assertFalse(current.isExpired());
        verify(appUserRepository).saveAndFlush(user);
        verify(auditService).record(argThat(e -> e.action() == AuditAction.AUTH_PASSWORD_CHANGED));
    }

    @Test
    void changePassword_WrongCurrentPasswordRejected() {
        AppUser user = user();
        loggedInAs(user);
        when(appUserRepository.findById(new AppUser.AppUserId(1L))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword(new ChangePasswordCommand("wrong", "Correct-Horse-7-Battery"), request));
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void changePassword_WeakPasswordRejected() {
        AppUser user = user();
        loggedInAs(user);
        when(appUserRepository.findById(new AppUser.AppUserId(1L))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        doThrow(new PasswordValidationException(List.of("Password must contain a digit"))).when(passwordValidator).validate("weak");

        assertThrows(PasswordValidationException.class,
                () -> authService.changePassword(new ChangePasswordCommand("old", "weak"), request));
        verify(appUserRepository, never()).saveAndFlush(any());
    }
}
