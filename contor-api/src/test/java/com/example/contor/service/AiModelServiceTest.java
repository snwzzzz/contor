package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.OllamaClient;
import com.example.contor.ai.OllamaException;
import com.example.contor.api.model.AiModelDto;
import com.example.contor.api.model.AiModelRole;
import com.example.contor.api.model.AiPullState;
import com.example.contor.api.model.AiStatusDto;
import com.example.contor.api.model.EmbeddingProgressDto;
import com.example.contor.config.AiProperties;
import com.example.contor.exception.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelServiceTest {

    @Mock
    private OllamaClient ollamaClient;

    @Mock
    private EmailEmbeddingService emailEmbeddingService;

    private final List<Runnable> pullTasks = new ArrayList<>();
    private AiModelService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                new AiProperties.Ollama(URI.create("http://127.0.0.1:11434"), Duration.ofMinutes(1), 8192, "10m"),
                new AiProperties.Models("qwen3.5:9b", "qwen3.5:9b", "nomic-embed-text"),
                new AiProperties.Processing(true, Duration.ofMinutes(2), 20), ZoneId.of("Europe/Vienna"));
        service = new AiModelService(properties, ollamaClient, new AiExecutors(Runnable::run, pullTasks::add), emailEmbeddingService);
    }

    @Test
    void status_UnreachableOllama() {
        when(ollamaClient.version()).thenReturn(Optional.empty());

        AiStatusDto status = service.status();

        assertThat(status.getReachable()).isFalse();
        assertThat(status.getReady()).isFalse();
        assertThat(status.getModels()).extracting(AiModelDto::getName).containsExactly("qwen3.5:9b", "nomic-embed-text");
        assertThat(status.getModels()).allSatisfy(model -> assertThat(model.getInstalled()).isFalse());
        verify(ollamaClient, never()).installedModels();
    }

    @Test
    void status_SharedModelListsAllItsRoles() {
        when(ollamaClient.version()).thenReturn(Optional.of("0.40.1"));
        when(ollamaClient.installedModels()).thenReturn(Set.of());

        AiStatusDto status = service.status();

        assertThat(status.getModels().getFirst().getRoles()).containsExactly(AiModelRole.EXTRACTION, AiModelRole.ROUTING);
        assertThat(status.getModels().get(1).getRoles()).containsExactly(AiModelRole.EMBEDDING);
    }

    @Test
    void status_ReadyWhenEveryModelIsInstalled() {
        when(ollamaClient.version()).thenReturn(Optional.of("0.40.1"));
        when(ollamaClient.installedModels()).thenReturn(Set.of("qwen3.5:9b", "nomic-embed-text:latest"));

        AiStatusDto status = service.status();

        assertThat(status.getReachable()).isTrue();
        assertThat(status.getVersion()).isEqualTo("0.40.1");
        assertThat(status.getReady()).isTrue();
        assertThat(status.getModels()).allSatisfy(model -> {
            assertThat(model.getInstalled()).isTrue();
            assertThat(model.getPullState()).isEqualTo(AiPullState.IDLE);
        });
    }

    @Test
    void pullMissingModels_PullsOnlyMissingModels() {
        when(ollamaClient.version()).thenReturn(Optional.of("0.40.1"));
        when(ollamaClient.installedModels()).thenReturn(Set.of("qwen3.5:9b"));

        AiStatusDto status = service.pullMissingModels();

        assertThat(pullTasks).hasSize(1);
        assertThat(status.getModels().get(1).getPullState()).isEqualTo(AiPullState.PULLING);
        assertThat(status.getModels().get(1).getPullPercent()).isZero();
        pullTasks.getFirst().run();
        verify(ollamaClient).pull(eq("nomic-embed-text"), any());
        verify(ollamaClient, never()).pull(eq("qwen3.5:9b"), any());
    }

    @Test
    void pullMissingModels_ProgressThenSuccessClearsTheState() {
        when(ollamaClient.version()).thenReturn(Optional.of("0.40.1"));
        when(ollamaClient.installedModels()).thenReturn(Set.of("qwen3.5:9b"));
        List<IntConsumer> listeners = new ArrayList<>();
        doAnswer(invocation -> {
            IntConsumer listener = invocation.getArgument(1);
            listener.accept(40);
            listeners.add(listener);
            return null;
        }).when(ollamaClient).pull(anyString(), any());

        service.pullMissingModels();
        pullTasks.getFirst().run();

        assertThat(listeners).hasSize(1);
        assertThat(service.status().getModels().get(1).getPullState()).isEqualTo(AiPullState.IDLE);
    }

    @Test
    void pullMissingModels_FailureIsReported() {
        when(ollamaClient.version()).thenReturn(Optional.of("0.40.1"));
        when(ollamaClient.installedModels()).thenReturn(Set.of("qwen3.5:9b"));
        doAnswer(invocation -> {
            throw new OllamaException("disk full");
        }).when(ollamaClient).pull(eq("nomic-embed-text"), any());

        service.pullMissingModels();
        pullTasks.getFirst().run();

        AiModelDto embedding = service.status().getModels().get(1);
        assertThat(embedding.getPullState()).isEqualTo(AiPullState.FAILED);
        assertThat(embedding.getPullError()).isEqualTo("disk full");
    }

    @Test
    void pullMissingModels_RunningPullIsNotStartedTwice() {
        when(ollamaClient.version()).thenReturn(Optional.of("0.40.1"));
        when(ollamaClient.installedModels()).thenReturn(Set.of("qwen3.5:9b"));

        service.pullMissingModels();
        service.pullMissingModels();

        assertThat(pullTasks).hasSize(1);
        verify(ollamaClient, times(0)).pull(anyString(), any());
    }

    @Test
    void pullMissingModels_UnreachableOllamaRejected() {
        when(ollamaClient.version()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pullMissingModels())
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("Ollama is not reachable");
        assertThat(pullTasks).isEmpty();
    }

    @Test
    void status_IncludesTheEmbeddingProgressOfTheCurrentUser() {
        when(ollamaClient.version()).thenReturn(Optional.empty());
        when(emailEmbeddingService.progress()).thenReturn(Optional.of(new EmbeddingProgressDto(40, 100, true)));

        AiStatusDto status = service.status();

        assertThat(status.getEmbeddings().getIndexed()).isEqualTo(40);
        assertThat(status.getEmbeddings().getTotal()).isEqualTo(100);
        assertThat(status.getEmbeddings().getRunning()).isTrue();
    }

    @Test
    void status_OmitsTheEmbeddingProgressWithoutAUser() {
        when(ollamaClient.version()).thenReturn(Optional.empty());

        assertThat(service.status().getEmbeddings()).isNull();
    }
}
