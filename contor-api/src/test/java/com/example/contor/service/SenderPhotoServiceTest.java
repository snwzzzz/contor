package com.example.contor.service;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.ContactPhoto;
import com.example.contor.domain.Email;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.SenderDomain;
import com.example.contor.domain.SenderLogo;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.ContactPhotoNotFoundException;
import com.example.contor.exception.SenderLogoNotFoundException;
import com.example.contor.persistence.ContactPhotoRepository;
import com.example.contor.persistence.SenderLogoRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SenderPhotoServiceTest {

    @Mock
    private SenderLogoRepository senderLogoRepository;

    @Mock
    private ContactPhotoRepository contactPhotoRepository;

    @Mock
    private LogoFetcher logoFetcher;

    @InjectMocks
    private SenderPhotoService senderPhotoService;

    @BeforeEach
    void login() {
        var principal = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listLogos_ReturnsCachedLogosWithoutFetching() {
        when(senderLogoRepository.findByDomainIn(anyCollection())).thenReturn(List.of(
                logo("siemens.com", new byte[] {1}, Instant.now()),
                logo("no-logo.example", null, Instant.now())));

        var domains = senderPhotoService.listLogos(List.of("siemens.com", "no-logo.example"));

        assertEquals(List.of("siemens.com"), domains);
        verifyNoInteractions(logoFetcher);
    }

    @Test
    void listLogos_FetchesUnknownDomainsAndCachesBothOutcomes() {
        when(senderLogoRepository.findByDomainIn(anyCollection())).thenReturn(List.of());
        when(logoFetcher.fetch(new SenderDomain("siemens.com"))).thenReturn(Optional.of(new ImageData(new byte[] {1}, "image/png")));
        when(logoFetcher.fetch(new SenderDomain("no-logo.example"))).thenReturn(Optional.empty());

        var domains = senderPhotoService.listLogos(List.of("Siemens.com", "no-logo.example"));

        assertEquals(List.of("siemens.com"), domains);
        verify(senderLogoRepository).saveAllAndFlush(argThat(logos -> {
            var list = (List<SenderLogo>) logos;
            return list.size() == 2 && list.stream().allMatch(logo -> logo.getFetchedAt() != null);
        }));
    }

    @Test
    void listLogos_RetriesMissingLogosAfterAWeek() {
        when(senderLogoRepository.findByDomainIn(anyCollection()))
                .thenReturn(List.of(logo("siemens.com", null, Instant.now().minus(Duration.ofDays(8)))));
        when(logoFetcher.fetch(new SenderDomain("siemens.com"))).thenReturn(Optional.of(new ImageData(new byte[] {1}, "image/png")));

        assertEquals(List.of("siemens.com"), senderPhotoService.listLogos(List.of("siemens.com")));
    }

    @Test
    void listLogos_IgnoresInvalidDomains() {
        assertEquals(List.of(), senderPhotoService.listLogos(List.of("localhost", "127.0.0.1", "evil.com/x")));
        verifyNoInteractions(logoFetcher, senderLogoRepository);
    }

    @Test
    void getLogo_ReturnsCachedImage() {
        when(senderLogoRepository.findByDomain(new SenderDomain("siemens.com")))
                .thenReturn(Optional.of(logo("siemens.com", new byte[] {7}, Instant.now())));

        var image = senderPhotoService.getLogo("siemens.com");

        assertArrayEquals(new byte[] {7}, image.bytes());
        assertEquals("image/png", image.contentType());
    }

    @Test
    void getLogo_NotFoundWhenTheDomainHasNone() {
        when(senderLogoRepository.findByDomain(new SenderDomain("siemens.com")))
                .thenReturn(Optional.of(logo("siemens.com", null, Instant.now())));

        assertThrows(SenderLogoNotFoundException.class, () -> senderPhotoService.getLogo("siemens.com"));
        assertThrows(SenderLogoNotFoundException.class, () -> senderPhotoService.getLogo("not a domain"));
    }

    @Test
    void listContactPhotos_ForCurrentUser() {
        when(contactPhotoRepository.findByAccount_Owner_Id(new AppUser.AppUserId(1L))).thenReturn(List.of(contact(1L)));

        var photos = senderPhotoService.listContactPhotos();

        assertEquals(1, photos.size());
        assertEquals(9L, photos.get(0).getId());
        assertEquals("bob@example.com", photos.get(0).getEmailAddress());
    }

    @Test
    void getContactPhoto_OnlyOwnContacts() {
        when(contactPhotoRepository.findById(new ContactPhoto.ContactPhotoId(9L))).thenReturn(Optional.of(contact(99L)));

        assertThrows(ContactPhotoNotFoundException.class, () -> senderPhotoService.getContactPhoto(9L));
    }

    @Test
    void getContactPhoto_ReturnsImage() {
        when(contactPhotoRepository.findById(new ContactPhoto.ContactPhotoId(9L))).thenReturn(Optional.of(contact(1L)));

        assertEquals("image/jpeg", senderPhotoService.getContactPhoto(9L).contentType());
        verify(senderLogoRepository, never()).saveAllAndFlush(any());
    }

    private static SenderLogo logo(String domain, byte[] image, Instant fetchedAt) {
        return SenderLogo.builder().id(new SenderLogo.SenderLogoId(1L)).domain(new SenderDomain(domain)).image(image)
                .contentType(image == null ? null : "image/png").fetchedAt(fetchedAt).build();
    }

    private static ContactPhoto contact(long ownerId) {
        var account = MailAccount.builder().id(new MailAccount.MailAccountId(2L))
                .owner(AppUser.builder().id(new AppUser.AppUserId(ownerId)).build())
                .provider(MailProvider.MICROSOFT_GRAPH).emailAddress(new Email("me@example.com")).build();
        return ContactPhoto.builder().id(new ContactPhoto.ContactPhotoId(9L)).account(account).emailAddress("bob@example.com")
                .image(new byte[] {1}).contentType("image/jpeg").build();
    }
}
