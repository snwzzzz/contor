package com.example.contor.service;

import com.example.contor.ai.AiExecutors;
import com.example.contor.api.model.ApiDiscoveryDto;
import com.example.contor.api.model.DiscoveryState;
import com.example.contor.api.model.EndpointOrigin;
import com.example.contor.api.model.NewApiDiscoveryCommand;
import com.example.contor.config.ExternalApiProperties;
import com.example.contor.domain.ApiPath;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.BaseUrl;
import com.example.contor.domain.EndpointSource;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.Slug;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.ApiDiscoveryNotFoundException;
import com.example.contor.exception.ExternalApiNotFoundException;
import com.example.contor.externalapi.ApiDiscoveryStore;
import com.example.contor.externalapi.DiscoveredEndpoint;
import com.example.contor.externalapi.OpenApiReader;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.security.OutboundRequestException;
import com.example.contor.security.OutboundRequestGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalApiDiscoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final ExternalApi.ExternalApiId SHOP_ID = new ExternalApi.ExternalApiId(7L);
    private static final String DOCS = "https://docs.shop.example/api";
    private static final String BASE = "https://api.shop.example/v1";
    private static final String OPENAPI = """
            {"openapi": "3.0.0", "info": {"title": "Shop", "version": "1"}, "servers": [{"url": "https://api.shop.example/v1"}],
             "paths": {"/orders": {"get": {"summary": "Orders"}}, "/products": {"get": {"summary": "Products"}}}}""";

    @Mock
    private ExternalApiRepository externalApiRepository;
    @Mock
    private ExternalApiEndpointRepository externalApiEndpointRepository;
    @Mock
    private OutboundRequestGuard guard;
    @Mock
    private ApiDocsModelService apiDocsModelService;
    @Mock
    private AiModelService aiModelService;

    private final List<Runnable> queued = new ArrayList<>();
    private ExternalApiDiscoveryService service;

    @BeforeEach
    void setUp() {
        var properties = new ExternalApiProperties(true, Duration.ofMinutes(1), Duration.ofSeconds(10), DataSize.ofMegabytes(1), 4, 30, 5);
        service = new ExternalApiDiscoveryService(externalApiRepository, externalApiEndpointRepository, guard,
                new OpenApiReader(JsonMapper.builder().build()), apiDocsModelService, aiModelService, new AiExecutors(queued::add, Runnable::run),
                new ApiDiscoveryStore(Clock.fixed(NOW, ZoneOffset.UTC)), properties);
        login(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void login(long userId) {
        var user = new AuthenticatedUser(userId, "user" + userId, UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private void docs(int status, String contentType, String body) {
        doReturn(new OutboundRequestGuard.Response(status, Map.of("content-type", contentType), body.getBytes(StandardCharsets.UTF_8), false))
                .when(guard).send(any());
    }

    private static NewApiDiscoveryCommand command() {
        return new NewApiDiscoveryCommand(DOCS, BASE);
    }

    @Test
    void start_OpenApiDocumentIsReadInCodeAtOnce() {
        docs(200, "application/json", OPENAPI);

        ApiDiscoveryDto dto = service.start(command());

        assertThat(dto.getState()).isEqualTo(DiscoveryState.DONE);
        assertThat(dto.getSource()).isEqualTo(EndpointOrigin.DOCS);
        assertThat(dto.getEndpoints()).extracting(endpoint -> endpoint.getPath()).containsExactly("/orders", "/products");
        verify(guard).send(argThat(request -> request.uri().equals(URI.create(DOCS)) && request.method().equals("GET")));
        verifyNoInteractions(apiDocsModelService);
        assertThat(queued).isEmpty();
    }

    @Test
    void start_OtherDocsAreReadByTheModelInTheBackground() {
        docs(200, "text/html; charset=utf-8", "<html><body><h1>Orders</h1><p>GET /orders lists your orders.</p></body></html>");
        when(aiModelService.isReady()).thenReturn(true);
        when(apiDocsModelService.readDocs(anyString(), eq(new BaseUrl(BASE)))).thenReturn(List.of(
                new DiscoveredEndpoint("Orders", new ApiPath("/orders"), Map.of(), null, null, null, EndpointSource.MODEL)));

        ApiDiscoveryDto running = service.start(command());

        assertThat(running.getState()).isEqualTo(DiscoveryState.RUNNING);
        assertThat(running.getEndpoints()).isEmpty();
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        verify(apiDocsModelService).readDocs(argThat(text -> text.contains("GET /orders lists your orders.") && !text.contains("<p>")),
                eq(new BaseUrl(BASE)));
        ApiDiscoveryDto done = service.find(running.getId());
        assertThat(done.getState()).isEqualTo(DiscoveryState.DONE);
        assertThat(done.getSource()).isEqualTo(EndpointOrigin.MODEL);
        assertThat(done.getEndpoints()).singleElement().satisfies(endpoint -> assertThat(endpoint.getName()).isEqualTo("Orders"));
    }

    @Test
    void start_ModelFailureMarksTheDiscoveryFailed() {
        docs(200, "text/plain", "GET /orders");
        when(aiModelService.isReady()).thenReturn(true);
        when(apiDocsModelService.readDocs(anyString(), any())).thenThrow(new IllegalStateException("Ollama is gone"));

        var running = service.start(command());
        queued.getFirst().run();

        var failed = service.find(running.getId());
        assertThat(failed.getState()).isEqualTo(DiscoveryState.FAILED);
        assertThat(failed.getError()).isEqualTo("The AI model could not read the documentation. Add the endpoints by hand.");
    }

    @Test
    void start_WithoutAReadyModelOnlyOpenApiWorks() {
        docs(200, "text/html", "<p>GET /orders</p>");
        when(aiModelService.isReady()).thenReturn(false);

        var dto = service.start(command());

        assertThat(dto.getState()).isEqualTo(DiscoveryState.FAILED);
        assertThat(dto.getError()).isEqualTo("This is no OpenAPI document, and the AI model is not ready to read it. Add the endpoints by hand.");
        assertThat(queued).isEmpty();
    }

    @Test
    void start_RefusedOrFailedFetchIsAFailedDiscovery() {
        when(guard.send(any())).thenThrow(new OutboundRequestException("docs.shop.example is not a public address"));

        var refused = service.start(command());

        assertThat(refused.getState()).isEqualTo(DiscoveryState.FAILED);
        assertThat(refused.getError()).isEqualTo("docs.shop.example is not a public address");

        docs(404, "text/html", "not here");
        assertThat(service.start(command()).getError()).isEqualTo("The documentation answered with HTTP 404");
    }

    @Test
    void start_EmptyPageFails() {
        docs(200, "text/html", "<html><body><script>app()</script></body></html>");

        assertThat(service.start(command()).getError()).isEqualTo("The documentation page has no text to read.");
        verifyNoInteractions(apiDocsModelService);
    }

    @Test
    void start_LeavesOutEndpointsTheAppAlreadyHas() {
        var shop = ExternalApi.builder().id(SHOP_ID).owner(AppUser.builder().id(OWNER).build()).name("Shop").slug(new Slug("shop"))
                .baseUrl(new BaseUrl(BASE)).build();
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));
        when(externalApiEndpointRepository.findByApi_IdOrderByNameAsc(SHOP_ID)).thenReturn(List.of(
                ExternalApiEndpoint.builder().name("My orders").path(new ApiPath("/orders")).build()));
        docs(200, "application/json", OPENAPI);

        var dto = service.start(command().apiId(7L));

        assertThat(dto.getEndpoints()).extracting(endpoint -> endpoint.getPath()).containsExactly("/products");
    }

    @Test
    void start_AnotherUsersAppNotFound() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(command().apiId(7L))).isInstanceOf(ExternalApiNotFoundException.class);
        verify(guard, never()).send(any());
    }

    @Test
    void start_InvalidUrlsRejectedBeforeAnythingIsFetched() {
        assertThatThrownBy(() -> service.start(new NewApiDiscoveryCommand("http://docs.shop.example", BASE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(new NewApiDiscoveryCommand(DOCS, "https://api.shop.example/v1?x=1")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(guard, never()).send(any());
    }

    @Test
    void find_OnlyTheUserWhoStartedItSeesIt() {
        docs(200, "application/json", OPENAPI);
        var dto = service.start(command());

        login(2L);

        assertThatThrownBy(() -> service.find(dto.getId())).isInstanceOf(ApiDiscoveryNotFoundException.class);
    }
}
