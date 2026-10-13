package com.example.contor.service;

import com.example.contor.config.DemoMailboxProperties;
import com.example.contor.config.DevAccountProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoMailboxImporterTest {

    @Mock
    private DemoMailboxService demoMailboxService;

    @Mock
    private AppUserRepository appUserRepository;

    private DemoMailboxImporter importer(boolean enabled, String devUsername) {
        return new DemoMailboxImporter(new DemoMailboxProperties(enabled, "demo/mailbox", true),
                new DevAccountProperties(devUsername, "dev@outlook.com", "secret"), appUserRepository, demoMailboxService);
    }

    @Test
    void run_DisabledDoesNothing() {
        importer(false, "dev").importDemoMailbox();

        verifyNoInteractions(demoMailboxService, appUserRepository);
    }

    @Test
    void run_ImportsIntoTheDevAccount() {
        AppUser dev = AppUser.builder().id(new AppUser.AppUserId(3L)).build();
        when(appUserRepository.findByUsername(new Username("dev"))).thenReturn(Optional.of(dev));

        importer(true, "dev").importDemoMailbox();

        verify(demoMailboxService).importMailbox(dev, Path.of("demo/mailbox"), true);
    }

    @Test
    void run_ImportFailureDoesNotStopTheStart() {
        AppUser dev = AppUser.builder().id(new AppUser.AppUserId(3L)).build();
        when(appUserRepository.findByUsername(new Username("dev"))).thenReturn(Optional.of(dev));
        when(demoMailboxService.importMailbox(any(), any(), anyBoolean())).thenThrow(new IllegalArgumentException("Demo mailbox folder not found"));

        assertThatCode(() -> importer(true, "dev").importDemoMailbox()).doesNotThrowAnyException();
    }

    @Test
    void run_WithoutDevAccountSkipped() {
        when(appUserRepository.findByUsername(new Username("dev"))).thenReturn(Optional.empty());

        importer(true, "dev").importDemoMailbox();

        verify(demoMailboxService, never()).importMailbox(any(), any(), anyBoolean());
    }

    @Test
    void run_WithoutConfiguredDevAccountSkipped() {
        importer(true, "").importDemoMailbox();

        verifyNoInteractions(demoMailboxService, appUserRepository);
    }
}
