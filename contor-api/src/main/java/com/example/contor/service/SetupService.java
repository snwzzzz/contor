package com.example.contor.service;

import com.example.contor.api.model.CurrentUserDto;
import com.example.contor.api.model.SetupCommand;
import com.example.contor.api.model.SetupStatusDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.exception.InvalidSetupTokenException;
import com.example.contor.exception.SetupNotAvailableException;
import com.example.contor.mapper.AppUserMapper;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.security.LogMasker;
import com.example.contor.security.SetupState;
import com.example.contor.security.SetupTokenHolder;
import com.example.contor.validation.PasswordValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** First-run setup: creates the OWNER with the one-time token. All input is validated before the token is consumed. */
@Slf4j
@RequiredArgsConstructor
@Service
public class SetupService {

    private final AppUserRepository appUserRepository;
    private final SetupState setupState;
    private final SetupTokenHolder setupTokenHolder;
    private final PasswordValidator passwordValidator;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public SetupStatusDto getStatus() {
        return new SetupStatusDto(setupState.isPending());
    }

    @Transactional
    public CurrentUserDto completeSetup(SetupCommand command) {
        if (!setupState.isPending()) {
            throw new SetupNotAvailableException();
        }
        if (!setupTokenHolder.matches(command.getToken())) {
            throw new InvalidSetupTokenException();
        }
        Username username = new Username(command.getUsername());
        Email email = new Email(command.getEmail());
        passwordValidator.validate(command.getPassword());
        if (!setupTokenHolder.consume(command.getToken())) {
            throw new InvalidSetupTokenException();
        }

        AppUser owner = appUserRepository.saveAndFlush(AppUser.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(command.getPassword()))
                .role(UserRole.OWNER)
                .build());

        auditService.record(AuditEvent.user(AuditAction.SETUP_COMPLETED, owner.getId().id()).entity("app_user", owner.getId().id()));
        log.info("Setup completed: owner '{}' ({}) created", username.value(), LogMasker.email(email.value()));
        return AppUserMapper.toDto(owner);
    }
}
