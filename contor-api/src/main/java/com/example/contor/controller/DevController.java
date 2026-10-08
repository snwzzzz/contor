package com.example.contor.controller;

import com.example.contor.api.DevApi;
import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.DevLoginDto;
import com.example.contor.service.DevLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Dev profile only (otherwise these paths do not exist). Request/response are request-scoped proxies. */
@Profile("dev")
@RequiredArgsConstructor
@RestController
public class DevController implements DevApi {

    private final DevLoginService devLoginService;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    @Override
    public ResponseEntity<DevLoginDto> getDevLogin() {
        return ResponseEntity.ok(devLoginService.getDevLogin(request));
    }

    @Override
    public ResponseEntity<CurrentUserDto> devLogin() {
        return ResponseEntity.ok(devLoginService.signIn(request, response));
    }
}
