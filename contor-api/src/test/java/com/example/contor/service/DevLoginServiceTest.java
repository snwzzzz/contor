package com.example.contor.service;

import com.example.contor.config.DevAccountProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.exception.DevLoginUnavailableException;
import com.example.contor.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevLoginServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuthService authService;

    @Mock
    private AuditService auditService;

    private DevLoginService service(String username) {
        return new DevLoginService(new DevAccountProperties(username, "dev@outlook.com", username == null ? null : "Dev-Password-12!"),
                appUserRepository, authService, auditService);
    }

    private static MockHttpServletRequest requestFrom(String address) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    @Test
    void getDevLogin_NamesTheDevAccount() {
        assertEquals("dev_user", service("dev_user").getDevLogin(requestFrom("127.0.0.1")).getUsername());
    }

    @Test
    void getDevLogin_UnavailableWithoutConfiguredAccount() {
        assertThrows(DevLoginUnavailableException.class, () -> service(null).getDevLogin(requestFrom("127.0.0.1")));
    }

    @Test
    void signIn_StartsASessionForTheDevAccount() {
        var user = AppUser.builder().id(new AppUser.AppUserId(1L)).username(new Username("dev_user"))
                .email(new Email("dev@outlook.com")).role(UserRole.OWNER).build();
        when(appUserRepository.findByUsername(new Username("dev_user"))).thenReturn(Optional.of(user));
        var request = requestFrom("::1");
        var response = new MockHttpServletResponse();

        var result = service("dev_user").signIn(request, response);

        assertEquals("dev_user", result.getUsername());
        verify(authService).startSession(user, request, response);
        verify(auditService).record(argThat(e -> e.action() == AuditAction.AUTH_DEV_LOGIN && e.actorUserId() == 1L));
    }

    @Test
    void signIn_RejectedFromOtherMachines() {
        assertThrows(AccessDeniedException.class,
                () -> service("dev_user").signIn(requestFrom("192.168.1.20"), new MockHttpServletResponse()));
        verify(authService, never()).startSession(any(), any(), any());
    }

    @Test
    void signIn_UnavailableWhenTheAccountDoesNotExist() {
        when(appUserRepository.findByUsername(new Username("dev_user"))).thenReturn(Optional.empty());

        assertThrows(DevLoginUnavailableException.class,
                () -> service("dev_user").signIn(requestFrom("127.0.0.1"), new MockHttpServletResponse()));
        verify(authService, never()).startSession(any(), any(), any());
    }
}
