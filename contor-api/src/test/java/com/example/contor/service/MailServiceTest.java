package com.example.contor.service;

import com.example.contor.api.model.EmailDto;
import com.example.contor.api.model.EmailPageDto;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.EmailLabel;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.HexColor;
import com.example.contor.domain.Label;
import com.example.contor.domain.LabelSource;
import com.example.contor.domain.MailAccount;
import com.example.contor.domain.MailAccountStatus;
import com.example.contor.domain.MailProvider;
import com.example.contor.domain.ProcessingState;
import com.example.contor.domain.Slug;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.EmailNotFoundException;
import com.example.contor.persistence.EmailLabelRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.persistence.LabelRepository;
import com.example.contor.persistence.MailAccountRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private MailAccountRepository mailAccountRepository;

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private EmailLabelRepository emailLabelRepository;

    @Mock
    private LabelRepository labelRepository;

    @InjectMocks
    private MailService mailService;

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

    private static MailAccount account(long ownerId) {
        return MailAccount.builder().id(new MailAccount.MailAccountId(2L)).owner(AppUser.builder().id(new AppUser.AppUserId(ownerId)).build())
                .provider(MailProvider.MICROSOFT_GRAPH).emailAddress(new Email("me@example.com")).status(MailAccountStatus.ACTIVE).build();
    }

    private static EmailMessage email(long ownerId) {
        return EmailMessage.builder().id(new EmailMessage.EmailMessageId(5L)).account(account(ownerId)).providerMessageId("AAMk")
                .subject("Shipped").receivedAt(Instant.parse("2026-01-01T10:00:00Z")).processingState(ProcessingState.NEW).build();
    }

    private void labelled() {
        when(emailLabelRepository.findById_EmailIdIn(List.of(5L))).thenReturn(List.of(
                EmailLabel.builder().id(new EmailLabel.EmailLabelId(5L, 3L)).source(LabelSource.MANUAL).build()));
        when(labelRepository.findAllById(List.of(new Label.LabelId(3L)))).thenReturn(List.of(
                Label.builder().id(new Label.LabelId(3L)).slug(new Slug("orders")).name("Orders").color(new HexColor("#00ff00")).build()));
    }

    @Test
    void listMailAccounts_OfTheCurrentUser() {
        when(mailAccountRepository.findByOwner_Id(new AppUser.AppUserId(1L))).thenReturn(List.of(account(1L)));

        assertEquals(1, mailService.listMailAccounts().size());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listEmails_NewestFirstWithLabels() {
        var pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "receivedAt"));
        when(emailMessageRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(email(1L)), pageable, 1));
        labelled();

        EmailPageDto page = mailService.listEmails(null, null, null, null, null, null);

        assertEquals(1L, page.getTotalElements());
        assertEquals("Shipped", page.getItems().get(0).getSubject());
        assertEquals("orders", page.getItems().get(0).getLabels().get(0).getSlug());
    }

    @Test
    void getEmail() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.of(email(1L)));
        labelled();

        EmailDto email = mailService.getEmail(5L);

        assertEquals("Shipped", email.getSubject());
        assertEquals(1, email.getLabels().size());
    }

    @Test
    void getEmail_OfAnotherUserIsNotFound() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.of(email(2L)));

        assertThrows(EmailNotFoundException.class, () -> mailService.getEmail(5L));
    }

    @Test
    void getEmail_NotFound() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(5L))).thenReturn(Optional.empty());

        assertThrows(EmailNotFoundException.class, () -> mailService.getEmail(5L));
    }
}
