package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.EmbeddingClient;
import com.example.contor.ai.OllamaException;
import com.example.contor.api.model.EmbeddingProgressDto;
import com.example.contor.config.AiProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.EmailEmbedding;
import com.example.contor.domain.EmailMessage;
import com.example.contor.domain.UserRole;
import com.example.contor.persistence.EmailEmbeddingRepository;
import com.example.contor.persistence.EmailMessageRepository;
import com.example.contor.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailEmbeddingServiceTest {

    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private EmailEmbeddingRepository emailEmbeddingRepository;

    @Mock
    private AiSettingsService aiSettingsService;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final List<Runnable> queued = new ArrayList<>();
    private EmailEmbeddingService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("router", "extractor", "embedder"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        service = new EmailEmbeddingService(emailMessageRepository, emailEmbeddingRepository, aiSettingsService, embeddingClient, properties,
                new AiExecutors(queued::add, Runnable::run), transactionManager);
        lenient().when(emailEmbeddingRepository.saveAndFlush(any(EmailEmbedding.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private static EmailMessage email(long id) {
        return EmailMessage.builder().id(new EmailMessage.EmailMessageId(id)).fromName("Anna Berger").fromAddress("anna@acme.com")
                .subject("Interview on Friday").bodyText("See you Friday.\n> quoted\n-- \nAnna").receivedAt(Instant.parse("2026-10-06T08:00:00Z")).build();
    }

    private static float[] vector() {
        float[] vector = new float[EmailEmbedding.DIMENSIONS];
        vector[0] = 1f;
        return vector;
    }

    private void emailExists(long id) {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(id))).thenReturn(Optional.of(email(id)));
    }

    @Test
    void documentText_IsPrefixedAndHasSubjectSenderAndTheCleanedBody() {
        assertThat(EmailEmbeddingService.documentText(email(1L))).isEqualTo("search_document: Interview on Friday\nAnna Berger\nSee you Friday.");
    }

    @Test
    void documentText_FallsBackToTheAddressAndTheSnippet() {
        EmailMessage email = EmailMessage.builder().fromAddress("anna@acme.com").subject(null).snippet("Short preview").build();

        assertThat(EmailEmbeddingService.documentText(email)).isEqualTo("search_document: \nanna@acme.com\nShort preview");
    }

    @Test
    void embedEmail_StoresTheVectorForTheConfiguredModel() {
        emailExists(5L);
        when(emailEmbeddingRepository.existsById(new EmailEmbedding.EmailEmbeddingId(5L, "embedder"))).thenReturn(false);
        when(embeddingClient.embed("search_document: Interview on Friday\nAnna Berger\nSee you Friday.")).thenReturn(vector());

        assertThat(service.embedEmail(5L)).isTrue();

        ArgumentCaptor<EmailEmbedding> saved = ArgumentCaptor.forClass(EmailEmbedding.class);
        verify(emailEmbeddingRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new EmailEmbedding.EmailEmbeddingId(5L, "embedder"));
        assertThat(saved.getValue().getEmbedding()).isEqualTo(vector());
    }

    @Test
    void embedEmail_SkipsAnEmailThatIsAlreadyEmbeddedForThisModel() {
        emailExists(5L);
        when(emailEmbeddingRepository.existsById(new EmailEmbedding.EmailEmbeddingId(5L, "embedder"))).thenReturn(true);

        assertThat(service.embedEmail(5L)).isFalse();

        verify(embeddingClient, never()).embed(anyString());
    }

    @Test
    void embedEmail_UnknownEmailIsSkipped() {
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(9L))).thenReturn(Optional.empty());

        assertThat(service.embedEmail(9L)).isFalse();
    }

    @Test
    void embedEmail_AVectorOfTheWrongSizeIsRejected() {
        emailExists(5L);
        when(embeddingClient.embed(anyString())).thenReturn(new float[3]);

        assertThatThrownBy(() -> service.embedEmail(5L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("768");
        verify(emailEmbeddingRepository, never()).saveAndFlush(any());
    }

    @Test
    void backfill_EmbedsEveryMissingEmailNewestFirstInBatchesThenDropsOtherModels() {
        when(aiSettingsService.onboardedUsers()).thenReturn(List.of(OWNER));
        when(emailMessageRepository.findIdsWithoutEmbedding(eq(OWNER), eq("embedder"), any(Pageable.class)))
                .thenReturn(List.of(5L, 4L)).thenReturn(List.of(3L)).thenReturn(List.of());
        for (long id : new long[] {5, 4, 3}) emailExists(id);
        when(embeddingClient.embed(anyString())).thenReturn(vector());

        assertThat(service.startBackfill()).isTrue();
        queued.forEach(Runnable::run);

        verify(emailEmbeddingRepository, times(3)).saveAndFlush(any());
        verify(emailEmbeddingRepository).deleteById_ModelNot("embedder");
    }

    @Test
    void backfill_StopsWhenTheModelIsUnavailableAndKeepsTheOldVectors() {
        when(aiSettingsService.onboardedUsers()).thenReturn(List.of(OWNER));
        when(emailMessageRepository.findIdsWithoutEmbedding(eq(OWNER), eq("embedder"), any(Pageable.class))).thenReturn(List.of(5L, 4L));
        emailExists(5L);
        when(embeddingClient.embed(anyString())).thenThrow(new OllamaException("model not found"));

        service.startBackfill();
        queued.forEach(Runnable::run);

        verify(emailEmbeddingRepository, never()).deleteById_ModelNot(anyString());
        verify(embeddingClient, times(1)).embed(anyString());
        // finished (with an error): a later start is possible again
        assertThat(service.startBackfill()).isTrue();
    }

    @Test
    void backfill_StopsWhenABatchStoresNothing() {
        when(aiSettingsService.onboardedUsers()).thenReturn(List.of(OWNER));
        when(emailMessageRepository.findIdsWithoutEmbedding(eq(OWNER), eq("embedder"), any(Pageable.class))).thenReturn(List.of(7L));
        when(emailMessageRepository.findById(new EmailMessage.EmailMessageId(7L))).thenReturn(Optional.empty());

        service.startBackfill();
        queued.forEach(Runnable::run);

        verify(emailMessageRepository, times(1)).findIdsWithoutEmbedding(any(), anyString(), any());
        verify(emailEmbeddingRepository, never()).deleteById_ModelNot(anyString());
    }

    @Test
    void backfill_RunsOnlyOnceAtATime() {
        when(aiSettingsService.onboardedUsers()).thenReturn(List.of(OWNER));
        when(emailMessageRepository.findIdsWithoutEmbedding(any(), anyString(), any())).thenReturn(List.of());

        assertThat(service.startBackfill()).isTrue();
        assertThat(service.startBackfill()).isFalse();
        queued.forEach(Runnable::run);
        assertThat(service.startBackfill()).isTrue();
    }

    @Test
    void backfill_DoesNothingWithoutOnboardedUsers() {
        when(aiSettingsService.onboardedUsers()).thenReturn(List.of());

        assertThat(service.startBackfill()).isFalse();

        assertThat(queued).isEmpty();
    }

    @Test
    void progress_CountsTheCurrentUsersIndexedAndIndexableMail() {
        var principal = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(emailEmbeddingRepository.countIndexed(OWNER, "embedder")).thenReturn(40L);
        when(emailMessageRepository.countIndexable(OWNER)).thenReturn(100L);

        Optional<EmbeddingProgressDto> progress = service.progress();

        assertThat(progress).hasValueSatisfying(value -> {
            assertThat(value.getIndexed()).isEqualTo(40);
            assertThat(value.getTotal()).isEqualTo(100);
            assertThat(value.getRunning()).isFalse();
        });
    }

    @Test
    void progress_IsEmptyWithoutASignedInUser() {
        assertThat(service.progress()).isEmpty();
    }

    @Test
    void backfillMissing_IsScheduledAndAsksForUsersToIndex() {
        when(aiSettingsService.onboardedUsers()).thenReturn(List.of());

        service.backfillMissing();

        verify(aiSettingsService).onboardedUsers();
    }
}
