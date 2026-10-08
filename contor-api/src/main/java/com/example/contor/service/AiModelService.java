package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.OllamaClient;
import com.example.contor.api.model.AiModelDto;
import com.example.contor.api.model.AiModelRole;
import com.example.contor.api.model.AiPullState;
import com.example.contor.api.model.AiStatusDto;
import com.example.contor.config.AiProperties;
import com.example.contor.exception.AiUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Status of the local Ollama and the configured models, and model downloads on the AI pull executor. Download progress
 * lives in memory only (a restart forgets it; Ollama resumes partial downloads on the next pull). No database access,
 * hence no transactions.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AiModelService {

    private record Pull(AiPullState state, int percent, String error) {
    }

    private final AiProperties properties;
    private final OllamaClient ollamaClient;
    private final AiExecutors aiExecutors;
    private final Map<String, Pull> pulls = new ConcurrentHashMap<>();

    public AiStatusDto status() {
        Optional<String> version = ollamaClient.version();
        Set<String> installed = version.isPresent() ? ollamaClient.installedModels() : Set.of();
        List<AiModelDto> models = new ArrayList<>();
        configuredModels().forEach((name, roles) -> {
            Pull pull = pulls.getOrDefault(name, new Pull(AiPullState.IDLE, 0, null));
            AiModelDto model = new AiModelDto(name, roles, isInstalled(name, installed), pull.state());
            if (pull.state() == AiPullState.PULLING) {
                model.pullPercent(pull.percent());
            }
            models.add(model.pullError(pull.error()));
        });
        boolean ready = version.isPresent() && models.stream().allMatch(AiModelDto::getInstalled);
        return new AiStatusDto(version.isPresent(), ready, models).version(version.orElse(null));
    }

    /** Whether Ollama is reachable and every configured model is installed. */
    public boolean isReady() {
        return status().getReady();
    }

    public AiStatusDto pullMissingModels() {
        AiStatusDto status = status();
        if (!status.getReachable()) {
            throw new AiUnavailableException("Ollama is not reachable at " + properties.ollama().baseUrl() + ". Start Ollama and try again.");
        }
        for (AiModelDto model : status.getModels()) {
            if (model.getInstalled() || model.getPullState() == AiPullState.PULLING) {
                continue;
            }
            String name = model.getName();
            pulls.put(name, new Pull(AiPullState.PULLING, 0, null));
            aiExecutors.pull().execute(() -> pull(name));
        }
        return status();
    }

    private void pull(String name) {
        try {
            log.info("Pulling Ollama model {}", name);
            ollamaClient.pull(name, percent -> pulls.put(name, new Pull(AiPullState.PULLING, percent, null)));
            pulls.remove(name);
            log.info("Pulled Ollama model {}", name);
        } catch (RuntimeException e) {
            log.warn("Pulling Ollama model {} failed: {}", name, e.getMessage());
            pulls.put(name, new Pull(AiPullState.FAILED, 0, e.getMessage()));
        }
    }

    /** Distinct configured model names with their roles, in role order. */
    private Map<String, List<AiModelRole>> configuredModels() {
        Map<String, List<AiModelRole>> models = new LinkedHashMap<>();
        models.computeIfAbsent(properties.models().extraction(), name -> new ArrayList<>()).add(AiModelRole.EXTRACTION);
        models.computeIfAbsent(properties.models().routing(), name -> new ArrayList<>()).add(AiModelRole.ROUTING);
        models.computeIfAbsent(properties.models().embedding(), name -> new ArrayList<>()).add(AiModelRole.EMBEDDING);
        return models;
    }

    /** Ollama lists untagged models with {@code :latest}. */
    private static boolean isInstalled(String name, Set<String> installed) {
        return installed.contains(name) || (!name.contains(":") && installed.contains(name + ":latest"));
    }
}
