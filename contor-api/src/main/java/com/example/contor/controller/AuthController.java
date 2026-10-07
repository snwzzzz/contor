package com.example.contor.controller;

import com.example.contor.api.AuthApi;
import com.example.contor.api.model.ChangePasswordCommand;
import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.LoginCommand;
import com.example.contor.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Request/response are request-scoped proxies; the session handling in AuthService needs them. */
@RequiredArgsConstructor
@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    /** The CSRF filter (SPA mode) writes the XSRF-TOKEN cookie on every request; nothing else to do. */
    @Override
    public ResponseEntity<Void> initCsrf() {
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CurrentUserDto> login(LoginCommand loginCommand) {
        return ResponseEntity.ok(authService.login(loginCommand, request, response));
    }

    @Override
    public ResponseEntity<Void> logout() {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CurrentUserDto> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordCommand changePasswordCommand) {
        authService.changePassword(changePasswordCommand, request);
        return ResponseEntity.noContent().build();
    }
}
