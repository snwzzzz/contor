package com.example.contor.service;

import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.Role;
import com.example.contor.api.model.SetupCommand;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.InvalidSetupTokenException;
import com.example.contor.exception.PasswordValidationException;
import com.example.contor.exception.SetupNotAvailableException;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.SetupState;
import com.example.contor.security.SetupTokenHolder;
import com.example.contor.validation.PasswordValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetupServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private SetupState setupState;

    @Mock
    private SetupTokenHolder setupTokenHolder;

    @Mock
    private PasswordValidator passwordValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SetupService setupService;

    private SetupCommand command() {
        return new SetupCommand("token", "owner", "owner@outlook.com", "Correct-Horse-7-Battery");
    }

    @Test
    void getStatus() {
        when(setupState.isPending()).thenReturn(true);

        assertTrue(setupService.getStatus().getSetupRequired());
    }

    @Test
    void completeSetup() {
        when(setupState.isPending()).thenReturn(true);
        when(setupTokenHolder.matches("token")).thenReturn(true);
        when(setupTokenHolder.consume("token")).thenReturn(true);
        when(passwordEncoder.encode("Correct-Horse-7-Battery")).thenReturn("argon-hash");
        when(appUserRepository.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(new AppUser.AppUserId(1L));
            return user;
        });

        CurrentUserDto result = setupService.completeSetup(command());

        assertEquals("owner", result.getUsername());
        assertEquals(Role.OWNER, result.getRole());
        verify(appUserRepository).saveAndFlush(argThat(u -> u.getRole() == UserRole.OWNER && u.getPasswordHash().equals("argon-hash")));
        verify(auditService).record(argThat(e -> e.action() == AuditAction.SETUP_COMPLETED && e.actorUserId() == 1L));
    }

    @Test
    void completeSetup_NotAvailableOnceAUserExists() {
        when(setupState.isPending()).thenReturn(false);

        assertThrows(SetupNotAvailableException.class, () -> setupService.completeSetup(command()));
        verify(setupTokenHolder, never()).consume(any());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeSetup_InvalidTokenRejected() {
        when(setupState.isPending()).thenReturn(true);
        when(setupTokenHolder.matches("token")).thenReturn(false);

        assertThrows(InvalidSetupTokenException.class, () -> setupService.completeSetup(command()));
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeSetup_TokenAlreadyConsumedRejected() {
        when(setupState.isPending()).thenReturn(true);
        when(setupTokenHolder.matches("token")).thenReturn(true);
        when(setupTokenHolder.consume("token")).thenReturn(false);

        assertThrows(InvalidSetupTokenException.class, () -> setupService.completeSetup(command()));
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeSetup_WeakPasswordRejectedWithoutConsumingToken() {
        when(setupState.isPending()).thenReturn(true);
        when(setupTokenHolder.matches("token")).thenReturn(true);
        doThrow(new PasswordValidationException(List.of("Password is too common"))).when(passwordValidator).validate(any());

        assertThrows(PasswordValidationException.class, () -> setupService.completeSetup(command()));
        verify(setupTokenHolder, never()).consume(any());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeSetup_InvalidEmailRejected() {
        when(setupState.isPending()).thenReturn(true);
        when(setupTokenHolder.matches("token")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> setupService.completeSetup(new SetupCommand("token", "owner", "nope", "Correct-Horse-7-Battery")));
        verify(setupTokenHolder, never()).consume(any());
    }

    @Test
    void completeSetup_NonOutlookEmailRejected() {
        when(setupState.isPending()).thenReturn(true);
        when(setupTokenHolder.matches("token")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> setupService.completeSetup(new SetupCommand("token", "owner", "owner@gmail.com", "Correct-Horse-7-Battery")));
        verify(setupTokenHolder, never()).consume(any());
        verify(appUserRepository, never()).saveAndFlush(any());
    }
}
