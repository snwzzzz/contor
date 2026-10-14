package com.example.contor.controller;

import com.example.contor.api.SetupApi;
import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.SetupCommand;
import com.example.contor.api.model.SetupStatusDto;
import com.example.contor.service.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class SetupController implements SetupApi {

    private final SetupService setupService;

    @Override
    public ResponseEntity<SetupStatusDto> getSetupStatus() {
        return ResponseEntity.ok(setupService.getStatus());
    }

    @Override
    public ResponseEntity<CurrentUserDto> completeSetup(SetupCommand setupCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(setupService.completeSetup(setupCommand));
    }
}
