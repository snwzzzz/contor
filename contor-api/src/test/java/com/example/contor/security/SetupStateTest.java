package com.example.contor.security;

import com.example.contor.persistence.AppUserRepository;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SetupStateTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private SetupState setupState;

    @Test
    void is_pending_while_no_user_exists() {
        when(appUserRepository.count()).thenReturn(0L);

        assertThat(setupState.isPending()).isTrue();
    }

    @Test
    void is_not_pending_once_any_user_exists() {
        when(appUserRepository.count()).thenReturn(1L);

        assertThat(setupState.isPending()).isFalse();
    }
}
