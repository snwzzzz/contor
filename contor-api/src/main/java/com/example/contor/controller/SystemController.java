package com.example.contor.controller;

import com.example.contor.api.SystemApi;
import com.example.contor.api.model.VersionDto;
import com.example.contor.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class SystemController implements SystemApi {

    private final SystemService systemService;

    @Override
    public ResponseEntity<VersionDto> getVersion() {
        return ResponseEntity.ok(systemService.getVersion());
    }
}
