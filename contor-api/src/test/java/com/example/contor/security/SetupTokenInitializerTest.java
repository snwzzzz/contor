package com.example.contor.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SetupTokenInitializerTest {

    @Mock
    private SetupState setupState;

    @Mock
    private SetupTokenHolder setupTokenHolder;

    @InjectMocks
    private SetupTokenInitializer initializer;

    @Test
    void issues_a_token_when_setup_is_pending() {
        when(setupState.isPending()).thenReturn(true);
        when(setupTokenHolder.issue()).thenReturn("token");

        initializer.onApplicationReady();

        verify(setupTokenHolder).issue();
    }

    @Test
    void issues_nothing_once_a_user_exists() {
        when(setupState.isPending()).thenReturn(false);

        initializer.onApplicationReady();

        verify(setupTokenHolder, never()).issue();
    }
}
