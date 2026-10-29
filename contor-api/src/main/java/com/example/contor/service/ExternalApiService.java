package com.example.contor.service;

import com.example.contor.api.model.ApiAuthConfigDto;
import com.example.contor.api.model.ApiAuthType;
import com.example.contor.api.model.ApiHeaderDto;
import com.example.contor.api.model.ApiTestResultDto;
import com.example.contor.api.model.EndpointMethod;
import com.example.contor.api.model.ExternalApiDto;
import com.example.contor.api.model.ExternalApiEndpointDto;
import com.example.contor.api.model.NewExternalApiCommand;
import com.example.contor.api.model.NewExternalApiEndpointCommand;
import com.example.contor.api.model.TestExternalApiCommand;
import com.example.contor.api.model.UpdateExternalApiCommand;
import com.example.contor.api.model.UpdateExternalApiEndpointCommand;
import com.example.contor.domain.ApiAuthConfig;
import com.example.contor.domain.ApiAuthKind;
import com.example.contor.domain.ApiHeader;
import com.example.contor.domain.ApiHttpMethod;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.ApiSecret;
import com.example.contor.domain.ApiStatus;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.EndpointSource;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.HttpsUrl;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.Slug;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.ExternalApiEndpointNotFoundException;
import com.example.contor.exception.ExternalApiNotFoundException;
import com.example.contor.externalapi.ApiAuthException;
import com.example.contor.externalapi.ApiAuthRegistry;
import com.example.contor.externalapi.ApiResponse;
import com.example.contor.externalapi.ApiSecretCodec;
import com.example.contor.externalapi.ExternalApiClient;
import com.example.contor.mapper.ExternalApiMapper;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiRepository;
import com.example.contor.security.CurrentActor;
import com.example.contor.security.OutboundRequestException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * External APIs ("Apps", ADR 0012): a user's private apps and their confirmed endpoints, and the test call from the
 * modal. Secrets are write-only: stored encrypted through {@link ApiSecretCodec}, never returned, logged or audited with
 * their values. Every change validates the auth kind's settings ({@link ApiAuthRegistry}) and clears the failure state,
 * so the next call is due right away.
 */
@RequiredArgsConstructor
@Service
public class ExternalApiService {

    static final int TEST_BODY_CHARS = 20_000;
    static final int MAX_ENDPOINTS = 50;
    private static final int MAX_NAME = 80;
    private static final int MAX_QUERY_PARAMS = 30;
    private static final int MIN_INTERVAL = 15;
    private static final int MAX_INTERVAL = 10_080;
    private static final int MAX_SLUG_BASE = 36;

    private final ExternalApiRepository externalApiRepository;
    private final ExternalApiEndpointRepository externalApiEndpointRepository;
    private final AppUserRepository appUserRepository;
    private final ApiAuthRegistry apiAuthRegistry;
    private final ApiSecretCodec apiSecretCodec;
    private final ExternalApiClient externalApiClient;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public List<ExternalApiDto> findAll() {
        return externalApiRepository.findByOwner_IdOrderByNameAsc(currentUser()).stream().map(this::toDto).toList();
    }

    @Transactional
    public ExternalApiDto findById(Long apiId) {
        return toDto(load(apiId));
    }

    @Transactional
    public ExternalApiDto createApi(NewExternalApiCommand command) {
        String name = name(command.getName());
        ApiAuthKind authKind = authKind(command.getAuthKind(), ApiAuthKind.NONE);
        ApiAuthConfig authConfig = authConfig(command.getAuthConfig());
        List<ApiHeader> headers = headers(command.getHeaders());
        ApiSecret secret = new ApiSecret(blankToNull(command.getSecret()), headers(command.getSecretHeaders()));
        requireDistinct(headers, secret.headers());
        apiAuthRegistry.handler(authKind).validate(authConfig, secret);

        ExternalApi api = ExternalApi.builder()
                .owner(appUserRepository.getReferenceById(currentUser()))
                .name(name)
                .slug(uniqueSlug(name))
                .description(blankToNull(command.getDescription()))
                .baseUrl(new BaseUrl(command.getBaseUrl()))
                .docsUrl(docsUrl(command.getDocsUrl()))
                .authKind(authKind)
                .authConfig(authConfig)
                .secret(apiSecretCodec.write(secret))
                .headers(new ArrayList<>(headers))
                .build();
        if (command.getPollIntervalMinutes() != null) api.setPollIntervalMinutes(interval(command.getPollIntervalMinutes()));
        if (command.getEnabled() != null) api.setEnabled(command.getEnabled());
        if (command.getAiAnalysis() != null) api.setAiAnalysis(command.getAiAnalysis());
        if (command.getDailyRequestLimit() != null) api.setDailyRequestLimit(command.getDailyRequestLimit());
        api.setNextCallAt(api.isEnabled() ? clock.instant() : null);

        externalApiRepository.saveAndFlush(api);
        auditService.record(AuditEvent.currentActor(AuditAction.EXTERNAL_API_CREATED).entity("external_api", api.getId().id())
                .after(summary(api)));
        return toDto(api);
    }

    @Transactional
    public ExternalApiDto updateApi(Long apiId, UpdateExternalApiCommand command) {
        ExternalApi api = load(apiId);
        ApiSecret current = apiSecretCodec.read(api);

        if (command.getName() != null) api.setName(name(command.getName()));
        if (command.getDescription() != null) api.setDescription(blankToNull(command.getDescription()));
        if (command.getBaseUrl() != null) api.setBaseUrl(new BaseUrl(command.getBaseUrl()));
        if (command.getDocsUrl() != null) api.setDocsUrl(docsUrl(command.getDocsUrl()));
        if (command.getAuthKind() != null) api.setAuthKind(authKind(command.getAuthKind(), api.getAuthKind()));
        if (command.getAuthConfig() != null) api.setAuthConfig(authConfig(command.getAuthConfig()));
        if (command.getHeaders() != null) api.setHeaders(new ArrayList<>(headers(command.getHeaders())));
        if (command.getPollIntervalMinutes() != null) api.setPollIntervalMinutes(interval(command.getPollIntervalMinutes()));
        if (command.getEnabled() != null) api.setEnabled(command.getEnabled());
        if (command.getAiAnalysis() != null) api.setAiAnalysis(command.getAiAnalysis());
        if (command.getDailyRequestLimit() != null) api.setDailyRequestLimit(command.getDailyRequestLimit());

        ApiSecret secret = new ApiSecret(command.getSecret() != null ? blankToNull(command.getSecret()) : current.value(),
                command.getSecretHeaders() != null ? headers(command.getSecretHeaders()) : current.headers());
        requireDistinct(api.getHeaders(), secret.headers());
        apiAuthRegistry.handler(api.getAuthKind()).validate(api.getAuthConfig(), secret);
        boolean secretChanged = !secret.equals(current);
        if (secretChanged) {
            api.setSecret(apiSecretCodec.write(secret));
        }
        api.setStatus(ApiStatus.ACTIVE);
        api.setFailureCount(0);
        api.setLastError(null);
        api.setNextCallAt(api.isEnabled() ? clock.instant() : null);

        externalApiRepository.saveAndFlush(api);
        auditService.record(AuditEvent.currentActor(AuditAction.EXTERNAL_API_UPDATED).entity("external_api", apiId).after(summary(api)));
        if (secretChanged) {
            auditService.record(AuditEvent.currentActor(AuditAction.EXTERNAL_API_SECRET_CHANGED).entity("external_api", apiId));
        }
        return toDto(api);
    }

    @Transactional
    public void deleteApi(Long apiId) {
        ExternalApi api = load(apiId);
        externalApiRepository.delete(api);
        auditService.record(AuditEvent.currentActor(AuditAction.EXTERNAL_API_DELETED).entity("external_api", apiId).before(summary(api)));
    }

    @Transactional
    public List<ExternalApiEndpointDto> findEndpoints(Long apiId) {
        ExternalApi api = load(apiId);
        return externalApiEndpointRepository.findByApi_IdOrderByNameAsc(api.getId()).stream().map(ExternalApiMapper::toDto).toList();
    }

    @Transactional
    public ExternalApiEndpointDto createEndpoint(Long apiId, NewExternalApiEndpointCommand command) {
        ExternalApi api = load(apiId);
        String name = name(command.getName());
        requireUniqueName(api, name, null);
        if (api.getEndpoints().size() >= MAX_ENDPOINTS) {
            throw new IllegalArgumentException("An app has at most " + MAX_ENDPOINTS + " endpoints");
        }
        ApiHttpMethod method = method(command.getMethod(), ApiHttpMethod.GET);
        ExternalApiEndpoint endpoint = ExternalApiEndpoint.builder()
                .api(api)
                .name(name)
                .method(method)
                .path(new ApiPath(command.getPath()))
                .query(query(command.getQuery()))
                .body(body(method, command.getBody()))
                .itemsPath(jsonPath(command.getItemsPath()))
                .itemKeyPath(jsonPath(command.getItemKeyPath()))
                .description(blankToNull(command.getDescription()))
                .source(EndpointSource.USER)
                .enabled(command.getEnabled() == null || command.getEnabled())
                .build();
        api.getEndpoints().add(endpoint);
        externalApiEndpointRepository.saveAndFlush(endpoint);
        return ExternalApiMapper.toDto(endpoint);
    }

    @Transactional
    public ExternalApiEndpointDto updateEndpoint(Long apiId, Long endpointId, UpdateExternalApiEndpointCommand command) {
        ExternalApi api = load(apiId);
        ExternalApiEndpoint endpoint = loadEndpoint(api, endpointId);

        if (command.getName() != null) {
            String name = name(command.getName());
            requireUniqueName(api, name, endpoint);
            endpoint.setName(name);
        }
        if (command.getMethod() != null) endpoint.setMethod(method(command.getMethod(), endpoint.getMethod()));
        if (command.getPath() != null) endpoint.setPath(new ApiPath(command.getPath()));
        if (command.getQuery() != null) endpoint.setQuery(query(command.getQuery()));
        if (command.getBody() != null) {
            endpoint.setBody(body(endpoint.getMethod(), command.getBody()));
        } else if (endpoint.getMethod() == ApiHttpMethod.GET) {
            endpoint.setBody(null);
        } else if (endpoint.getBody() == null) {
            endpoint.setBody(new HashMap<>());
        }
        if (command.getItemsPath() != null) endpoint.setItemsPath(jsonPath(command.getItemsPath()));
        if (command.getItemKeyPath() != null) endpoint.setItemKeyPath(jsonPath(command.getItemKeyPath()));
        if (command.getDescription() != null) endpoint.setDescription(blankToNull(command.getDescription()));
        if (command.getEnabled() != null) endpoint.setEnabled(command.getEnabled());

        externalApiEndpointRepository.saveAndFlush(endpoint);
        return ExternalApiMapper.toDto(endpoint);
    }

    @Transactional
    public void deleteEndpoint(Long apiId, Long endpointId) {
        ExternalApi api = load(apiId);
        ExternalApiEndpoint endpoint = loadEndpoint(api, endpointId);
        api.getEndpoints().remove(endpoint);
        externalApiEndpointRepository.delete(endpoint);
    }

    /**
     * One call with unsaved settings; nothing is stored. With {@code apiId}, a missing secret or missing secret headers
     * come from that app. A refused or failed call is a result with {@code ok = false}; invalid settings throw.
     */
    @Transactional
    public ApiTestResultDto testCall(TestExternalApiCommand command) {
        ApiSecret stored = command.getApiId() != null ? apiSecretCodec.read(load(command.getApiId())) : ApiSecret.EMPTY;
        List<ApiHeader> headers = headers(command.getHeaders());
        ApiSecret secret = new ApiSecret(command.getSecret() != null ? blankToNull(command.getSecret()) : stored.value(),
                command.getSecretHeaders() != null ? headers(command.getSecretHeaders()) : stored.headers());
        requireDistinct(headers, secret.headers());
        ExternalApi api = ExternalApi.builder()
                .name("Test call")
                .baseUrl(new BaseUrl(command.getBaseUrl()))
                .authKind(authKind(command.getAuthKind(), ApiAuthKind.NONE))
                .authConfig(authConfig(command.getAuthConfig()))
                .secret(apiSecretCodec.write(secret))
                .headers(headers)
                .build();
        ApiHttpMethod method = method(command.getMethod(), ApiHttpMethod.GET);
        ExternalApiEndpoint endpoint = ExternalApiEndpoint.builder()
                .api(api)
                .name("test")
                .method(method)
                .path(new ApiPath(command.getPath()))
                .query(query(command.getQuery()))
                .body(body(method, command.getBody()))
                .build();

        long start = System.nanoTime();
        try {
            ApiResponse response = externalApiClient.call(api, endpoint);
            String body = response.body();
            boolean cut = body.length() > TEST_BODY_CHARS;
            return new ApiTestResultDto(response.isSuccess(), response.truncated() || cut, millisSince(start))
                    .status(response.status())
                    .contentType(response.contentType())
                    .body(cut ? body.substring(0, TEST_BODY_CHARS) : body);
        } catch (OutboundRequestException | ApiAuthException e) {
            return new ApiTestResultDto(false, false, millisSince(start)).error(e.getMessage());
        }
    }

    private ExternalApi load(Long apiId) {
        return externalApiRepository.findByIdAndOwner_Id(new ExternalApi.ExternalApiId(apiId), currentUser())
                .orElseThrow(() -> new ExternalApiNotFoundException(apiId));
    }

    private ExternalApiEndpoint loadEndpoint(ExternalApi api, Long endpointId) {
        return externalApiEndpointRepository.findByIdAndApi_Id(new ExternalApiEndpoint.ExternalApiEndpointId(endpointId), api.getId())
                .orElseThrow(() -> new ExternalApiEndpointNotFoundException(endpointId));
    }

    private ExternalApiDto toDto(ExternalApi api) {
        return ExternalApiMapper.toDto(api, apiSecretCodec.read(api));
    }

    /** A slug from the name (letters without accents, digits, underscores), unique among the user's apps. */
    private Slug uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "app";
        } else if (!Character.isLetter(base.charAt(0))) {
            base = "app_" + base;
        }
        if (base.length() > MAX_SLUG_BASE) {
            base = base.substring(0, MAX_SLUG_BASE).replaceAll("_+$", "");
        }
        if (!isValidSlug(base)) {
            base = base + "_app";
        }
        Slug slug = new Slug(base);
        for (int n = 2; externalApiRepository.existsByOwner_IdAndSlug(currentUser(), slug); n++) {
            slug = new Slug(base + "_" + n);
        }
        return slug;
    }

    private static boolean isValidSlug(String value) {
        try {
            new Slug(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void requireUniqueName(ExternalApi api, String name, ExternalApiEndpoint self) {
        boolean taken = api.getEndpoints().stream()
                .anyMatch(endpoint -> endpoint != self && endpoint.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new DuplicateResourceException("The app already has an endpoint named " + name);
        }
    }

    /** A header name may be given once, plain or secret (any case). */
    private static void requireDistinct(List<ApiHeader> headers, List<ApiHeader> secretHeaders) {
        Map<String, String> seen = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ApiHeader header : Stream.concat(headers.stream(), secretHeaders.stream()).toList()) {
            String first = seen.putIfAbsent(header.name(), header.name());
            if (first != null) throw new IllegalArgumentException("The header " + first + " is given twice");
        }
    }

    private static String name(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("The name is missing");
        if (trimmed.length() > MAX_NAME) throw new IllegalArgumentException("The name is longer than " + MAX_NAME + " characters");
        return trimmed;
    }

    private static int interval(int minutes) {
        if (minutes < MIN_INTERVAL || minutes > MAX_INTERVAL) {
            throw new IllegalArgumentException("The interval must be between " + MIN_INTERVAL + " and " + MAX_INTERVAL + " minutes");
        }
        return minutes;
    }

    private static ApiAuthKind authKind(ApiAuthType type, ApiAuthKind fallback) {
        return type != null ? ApiAuthKind.valueOf(type.name()) : fallback;
    }

    private static ApiAuthConfig authConfig(ApiAuthConfigDto dto) {
        if (dto == null) return ApiAuthConfig.NONE;
        return new ApiAuthConfig(blankToNull(dto.getHeaderName()), blankToNull(dto.getQueryParam()), blankToNull(dto.getTokenUrl()),
                blankToNull(dto.getScope()), blankToNull(dto.getUsername()), blankToNull(dto.getClientId()));
    }

    private static List<ApiHeader> headers(List<ApiHeaderDto> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream().map(dto -> new ApiHeader(dto.getName(), dto.getValue())).toList();
    }

    private static HttpsUrl docsUrl(String value) {
        String url = blankToNull(value);
        return url != null ? new HttpsUrl(url) : null;
    }

    private static ApiHttpMethod method(EndpointMethod method, ApiHttpMethod fallback) {
        return method != null ? ApiHttpMethod.valueOf(method.name()) : fallback;
    }

    private static Map<String, String> query(Map<String, String> query) {
        if (query == null) return new LinkedHashMap<>();
        if (query.size() > MAX_QUERY_PARAMS) {
            throw new IllegalArgumentException("An endpoint has at most " + MAX_QUERY_PARAMS + " query parameters");
        }
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("Query parameters need a name and a value");
            }
        }
        return new LinkedHashMap<>(query);
    }

    /** A GET has no body; a POST read query sends its body, or an empty object. */
    private static Map<String, Object> body(ApiHttpMethod method, Map<String, Object> body) {
        if (method == ApiHttpMethod.GET) {
            if (body != null) throw new IllegalArgumentException("Only a POST read query has a body");
            return null;
        }
        return body != null ? new LinkedHashMap<>(body) : new HashMap<>();
    }

    private static JsonPathExpr jsonPath(String value) {
        String path = blankToNull(value);
        return path != null ? new JsonPathExpr(path) : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> summary(ExternalApi api) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", api.getName());
        summary.put("baseUrl", api.getBaseUrl().value());
        summary.put("authKind", api.getAuthKind().name());
        summary.put("enabled", api.isEnabled());
        return summary;
    }

    private static int millisSince(long start) {
        return (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    private static AppUser.AppUserId currentUser() {
        return new AppUser.AppUserId(CurrentActor.userId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user")));
    }
}
