package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.ai.HtmlText;
import com.example.contor.api.model.ApiDiscoveryDto;
import com.example.contor.api.model.NewApiDiscoveryCommand;
import com.example.contor.config.ExternalApiProperties;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.EndpointSource;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.HttpsUrl;
import com.example.contor.exception.ApiDiscoveryNotFoundException;
import com.example.contor.exception.ExternalApiNotFoundException;
import com.example.contor.externalapi.ApiDiscovery;
import com.example.contor.externalapi.ApiDiscoveryStore;
import com.example.contor.externalapi.DiscoveredEndpoint;
import com.example.contor.externalapi.OpenApiReader;
import com.example.contor.mapper.ExternalApiMapper;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiRepository;
import com.example.contor.security.CurrentActor;
import com.example.contor.security.OutboundRequestException;
import com.example.contor.security.OutboundRequestGuard;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * "Read the docs" (ADR 0012): fetches an app's documentation through the {@link OutboundRequestGuard} and suggests
 * endpoints. An OpenAPI document is read in code ({@link OpenApiReader}) and done at once; other docs go to the model
 * ({@link ApiDocsModelService}) on the AI thread, and the user polls {@link #find}. Runs are kept in memory
 * ({@link ApiDiscoveryStore}); nothing is saved until the user adds the endpoints.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ExternalApiDiscoveryService {

    /** Documentation is often larger than an API answer (full OpenAPI documents). */
    static final int DOCS_MAX_BYTES = 4 * 1024 * 1024;
    private static final Map<String, String> HEADERS = Map.of(
            "Accept", "application/json, application/yaml;q=0.9, text/yaml;q=0.9, text/html;q=0.8, text/plain;q=0.7, */*;q=0.1",
            "User-Agent", "contor/1.0 (external API docs)");

    private final ExternalApiRepository externalApiRepository;
    private final ExternalApiEndpointRepository externalApiEndpointRepository;
    private final OutboundRequestGuard guard;
    private final OpenApiReader openApiReader;
    private final ApiDocsModelService apiDocsModelService;
    private final AiModelService aiModelService;
    private final AiExecutors aiExecutors;
    private final ApiDiscoveryStore store;
    private final ExternalApiProperties properties;

    /**
     * Reads {@code docsUrl}; a refused or failed fetch, docs without text or a model that is not ready make a FAILED
     * discovery, not an exception.
     *
     * @throws IllegalArgumentException     for a docs URL or base URL that is not valid HTTPS (nothing is fetched)
     * @throws ExternalApiNotFoundException when {@code apiId} is not one of the user's apps
     */
    @Transactional
    public ApiDiscoveryDto start(NewApiDiscoveryCommand command) {
        AppUser.AppUserId owner = currentUser();
        HttpsUrl docsUrl = new HttpsUrl(command.getDocsUrl().strip());
        BaseUrl base = new BaseUrl(command.getBaseUrl().strip());
        Set<String> existing = command.getApiId() != null ? existingEndpoints(command.getApiId(), owner) : Set.of();

        ApiDiscovery discovery = store.start(owner);
        OutboundRequestGuard.Response response;
        try {
            response = guard.send(OutboundRequestGuard.Request.get(URI.create(docsUrl.value()), HEADERS, properties.timeout(), DOCS_MAX_BYTES));
        } catch (OutboundRequestException e) {
            return ExternalApiMapper.toDto(store.fail(discovery.id(), e.getMessage()));
        }
        if (response.status() < 200 || response.status() >= 300) {
            return ExternalApiMapper.toDto(store.fail(discovery.id(), "The documentation answered with HTTP " + response.status()));
        }
        String body = new String(response.body(), StandardCharsets.UTF_8);

        Optional<List<DiscoveredEndpoint>> parsed = openApiReader.read(body, base);
        if (parsed.isPresent()) {
            return ExternalApiMapper.toDto(store.finish(discovery.id(), EndpointSource.DOCS, withoutExisting(parsed.get(), existing)));
        }
        String text = isHtml(response.contentType(), body) ? HtmlText.toText(body) : body.strip();
        if (text.isBlank()) {
            return ExternalApiMapper.toDto(store.fail(discovery.id(), "The documentation page has no text to read."));
        }
        if (!aiModelService.isReady()) {
            return ExternalApiMapper.toDto(store.fail(discovery.id(),
                    "This is no OpenAPI document, and the AI model is not ready to read it. Add the endpoints by hand."));
        }
        aiExecutors.analysis().execute(() -> {
            try {
                store.finish(discovery.id(), EndpointSource.MODEL, withoutExisting(apiDocsModelService.readDocs(text, base), existing));
            } catch (RuntimeException e) {
                log.warn("Reading API docs with the model failed: {}", e.getClass().getSimpleName());
                store.fail(discovery.id(), "The AI model could not read the documentation. Add the endpoints by hand.");
            }
        });
        return ExternalApiMapper.toDto(discovery);
    }

    @Transactional
    public ApiDiscoveryDto find(String discoveryId) {
        return store.find(discoveryId, currentUser()).map(ExternalApiMapper::toDto)
                .orElseThrow(() -> new ApiDiscoveryNotFoundException(discoveryId));
    }

    private Set<String> existingEndpoints(Long apiId, AppUser.AppUserId owner) {
        ExternalApi api = externalApiRepository.findByIdAndOwner_Id(new ExternalApi.ExternalApiId(apiId), owner)
                .orElseThrow(() -> new ExternalApiNotFoundException(apiId));
        return externalApiEndpointRepository.findByApi_IdOrderByNameAsc(api.getId()).stream()
                .map(endpoint -> key(endpoint.getPath().value(), endpoint.getQuery()))
                .collect(Collectors.toSet());
    }

    private static List<DiscoveredEndpoint> withoutExisting(List<DiscoveredEndpoint> endpoints, Set<String> existing) {
        return endpoints.stream().filter(endpoint -> !existing.contains(key(endpoint.path().value(), endpoint.query()))).toList();
    }

    private static String key(String path, Map<String, String> query) {
        return path + "?" + new TreeMap<>(query);
    }

    private static boolean isHtml(String contentType, String body) {
        return (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("html")) || body.stripLeading().startsWith("<");
    }

    private static AppUser.AppUserId currentUser() {
        return new AppUser.AppUserId(CurrentActor.userId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user")));
    }
}
