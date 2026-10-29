package com.example.contor.service;

import com.example.contor.api.model.ApiAuthConfigDto;
import com.example.contor.api.model.ApiAuthType;
import com.example.contor.api.model.ApiCallResult;
import com.example.contor.api.model.ApiHeaderDto;
import com.example.contor.api.model.EndpointMethod;
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
import com.example.contor.domain.CallOutcome;
import com.example.contor.domain.Collection;
import com.example.contor.domain.EndpointSource;
import com.example.contor.domain.ExternalApi;
import com.example.contor.domain.ExternalApiCall;
import com.example.contor.domain.ExternalApiEndpoint;
import com.example.contor.domain.ExternalApiMapping;
import com.example.contor.domain.JsonPathExpr;
import com.example.contor.domain.MappingState;
import com.example.contor.domain.Slug;
import com.example.contor.domain.UserRole;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.ExternalApiDisabledException;
import com.example.contor.exception.ExternalApiEndpointNotFoundException;
import com.example.contor.exception.ExternalApiNotFoundException;
import com.example.contor.externalapi.ApiAuthException;
import com.example.contor.externalapi.ApiAuthRegistry;
import com.example.contor.externalapi.ApiKeyHeaderAuthHandler;
import com.example.contor.externalapi.ApiKeyQueryAuthHandler;
import com.example.contor.externalapi.ApiResponse;
import com.example.contor.externalapi.ApiSecretCodec;
import com.example.contor.externalapi.BasicAuthHandler;
import com.example.contor.externalapi.BearerAuthHandler;
import com.example.contor.externalapi.ClientCredentialsAuthHandler;
import com.example.contor.externalapi.ExternalApiClient;
import com.example.contor.externalapi.NoAuthHandler;
import com.example.contor.persistence.AppUserRepository;
import com.example.contor.persistence.ExternalApiCallRepository;
import com.example.contor.persistence.ExternalApiEndpointRepository;
import com.example.contor.persistence.ExternalApiMappingRepository;
import com.example.contor.persistence.ExternalApiRepository;
import com.example.contor.security.AuthenticatedUser;
import com.example.contor.security.OutboundRequestException;
import com.example.contor.security.OutboundRequestGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalApiServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final AppUser.AppUserId OWNER = new AppUser.AppUserId(1L);
    private static final ExternalApi.ExternalApiId SHOP_ID = new ExternalApi.ExternalApiId(7L);

    @Mock
    private ExternalApiRepository externalApiRepository;

    @Mock
    private ExternalApiEndpointRepository externalApiEndpointRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ExternalApiCallRepository externalApiCallRepository;

    @Mock
    private ExternalApiMappingRepository externalApiMappingRepository;

    @Mock
    private ExternalApiClient externalApiClient;

    @Mock
    private AuditService auditService;

    private final ApiSecretCodec codec = new ApiSecretCodec(JsonMapper.builder().build());
    private ExternalApiService service;

    @BeforeEach
    void setUp() {
        var registry = new ApiAuthRegistry(List.of(new NoAuthHandler(), new ApiKeyHeaderAuthHandler(), new ApiKeyQueryAuthHandler(),
                new BearerAuthHandler(), new BasicAuthHandler(),
                new ClientCredentialsAuthHandler(mock(OutboundRequestGuard.class), JsonMapper.builder().build(), Clock.systemUTC())));
        service = new ExternalApiService(externalApiRepository, externalApiEndpointRepository, externalApiCallRepository, externalApiMappingRepository,
                appUserRepository,
                registry, codec, externalApiClient, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(appUserRepository.getReferenceById(OWNER)).thenReturn(AppUser.builder().id(OWNER).build());
        lenient().when(externalApiRepository.saveAndFlush(any(ExternalApi.class))).thenAnswer(inv -> {
            ExternalApi api = inv.getArgument(0);
            if (api.getId() == null) {
                api.setId(SHOP_ID);
                api.setCreatedAt(NOW);
            }
            return api;
        });
        lenient().when(externalApiEndpointRepository.saveAndFlush(any(ExternalApiEndpoint.class))).thenAnswer(inv -> {
            ExternalApiEndpoint endpoint = inv.getArgument(0);
            if (endpoint.getId() == null) {
                endpoint.setId(new ExternalApiEndpoint.ExternalApiEndpointId(30L));
                endpoint.setCreatedAt(NOW);
            }
            return endpoint;
        });
        var user = new AuthenticatedUser(1L, "owner", UserRole.OWNER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findAll_ListsTheCurrentUsersApps() {
        when(externalApiRepository.findByOwner_IdOrderByNameAsc(OWNER)).thenReturn(List.of(shop()));

        assertThat(service.findAll()).singleElement().satisfies(dto -> assertThat(dto.getName()).isEqualTo("Shop"));
    }

    @Test
    void findById_NotFoundForAnotherUsersApp() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(7L)).isInstanceOf(ExternalApiNotFoundException.class);
    }

    @Test
    void createApi_StoresTheAppWithEncryptedSecretAndAuditsWithoutIt() {
        var dto = service.createApi(new NewExternalApiCommand("Shop", "https://API.shop.example/v1/")
                .description("Orders")
                .authKind(ApiAuthType.API_KEY_HEADER)
                .authConfig(new ApiAuthConfigDto().headerName("X-Api-Key"))
                .secret("key-123")
                .headers(List.of(new ApiHeaderDto("Accept", "application/json")))
                .secretHeaders(List.of(new ApiHeaderDto("X-Shop-Token", "t-1"))));

        ExternalApi saved = savedApi();
        assertThat(saved.getOwner().getId()).isEqualTo(OWNER);
        assertThat(saved.getSlug()).isEqualTo(new Slug("shop"));
        assertThat(saved.getBaseUrl()).isEqualTo(new BaseUrl("https://api.shop.example/v1"));
        assertThat(saved.getAuthKind()).isEqualTo(ApiAuthKind.API_KEY_HEADER);
        assertThat(saved.getAuthConfig().headerName()).isEqualTo("X-Api-Key");
        assertThat(codec.read(saved)).isEqualTo(new ApiSecret("key-123", List.of(new ApiHeader("X-Shop-Token", "t-1"))));
        assertThat(saved.getHeaders()).containsExactly(new ApiHeader("Accept", "application/json"));
        assertThat(saved.getPollIntervalMinutes()).isEqualTo(360);
        assertThat(saved.getNextCallAt()).isEqualTo(NOW);
        assertThat(dto.getHasSecret()).isTrue();
        assertThat(dto.getSecretHeaderNames()).containsExactly("X-Shop-Token");

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.EXTERNAL_API_CREATED);
        assertThat(event.getValue().entityId()).isEqualTo(7L);
        assertThat(event.getValue().toString()).doesNotContain("key-123").doesNotContain("t-1");
    }

    @Test
    void createApi_DerivesAUniqueSlugFromTheName() {
        when(externalApiRepository.existsByOwner_IdAndSlug(OWNER, new Slug("shop_osterreich"))).thenReturn(true);
        when(externalApiRepository.existsByOwner_IdAndSlug(OWNER, new Slug("shop_osterreich_2"))).thenReturn(false);

        service.createApi(new NewExternalApiCommand("Shop Österreich!", "https://api.shop.example"));

        assertThat(savedApi().getSlug()).isEqualTo(new Slug("shop_osterreich_2"));
    }

    @Test
    void createApi_NamesWithoutLettersStillGetASlug() {
        service.createApi(new NewExternalApiCommand("123 Jobs", "https://api.jobs.example"));
        assertThat(savedApi().getSlug()).isEqualTo(new Slug("app_123_jobs"));
    }

    @Test
    void createApi_ReservedWordsGetASuffix() {
        service.createApi(new NewExternalApiCommand("User", "https://api.users.example"));
        assertThat(savedApi().getSlug()).isEqualTo(new Slug("user_app"));
    }

    @Test
    void createApi_DisabledAppIsNotDue() {
        service.createApi(new NewExternalApiCommand("Shop", "https://api.shop.example").enabled(false));

        assertThat(savedApi().isEnabled()).isFalse();
        assertThat(savedApi().getNextCallAt()).isNull();
    }

    @Test
    void createApi_MissingCredentialsRejected() {
        assertThatThrownBy(() -> service.createApi(new NewExternalApiCommand("Shop", "https://api.shop.example")
                .authKind(ApiAuthType.BEARER))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("token");
        verify(externalApiRepository, never()).saveAndFlush(any());
    }

    @Test
    void createApi_PlainHttpRejected() {
        assertThatThrownBy(() -> service.createApi(new NewExternalApiCommand("Shop", "http://api.shop.example")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(externalApiRepository, never()).saveAndFlush(any());
    }

    @Test
    void createApi_DuplicateHeaderNamesRejected() {
        assertThatThrownBy(() -> service.createApi(new NewExternalApiCommand("Shop", "https://api.shop.example")
                .headers(List.of(new ApiHeaderDto("Accept", "a")))
                .secretHeaders(List.of(new ApiHeaderDto("accept", "b")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Accept");
        verify(externalApiRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateApi_ChangesGivenFieldsAndClearsTheFailureState() {
        var shop = shop();
        shop.setStatus(ApiStatus.NEEDS_SETUP);
        shop.setFailureCount(4);
        shop.setLastError("Credentials rejected (401)");
        shop.setSecret(codec.write(new ApiSecret("key-1", List.of())));
        shop.setAuthKind(ApiAuthKind.BEARER);
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        var dto = service.updateApi(7L, new UpdateExternalApiCommand().name("Web shop").pollIntervalMinutes(60));

        assertThat(shop.getName()).isEqualTo("Web shop");
        assertThat(shop.getSlug()).isEqualTo(new Slug("shop"));
        assertThat(shop.getPollIntervalMinutes()).isEqualTo(60);
        assertThat(shop.getStatus()).isEqualTo(ApiStatus.ACTIVE);
        assertThat(shop.getFailureCount()).isZero();
        assertThat(shop.getLastError()).isNull();
        assertThat(shop.getNextCallAt()).isEqualTo(NOW);
        assertThat(codec.read(shop).value()).isEqualTo("key-1");
        assertThat(dto.getHasSecret()).isTrue();
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.EXTERNAL_API_UPDATED);
    }

    @Test
    void updateApi_NewSecretIsStoredAndAuditedAsSecretChange() {
        var shop = shop();
        shop.setAuthKind(ApiAuthKind.BEARER);
        shop.setSecret(codec.write(new ApiSecret("old", List.of(new ApiHeader("X-Shop-Token", "t-1")))));
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        service.updateApi(7L, new UpdateExternalApiCommand().secret("new"));

        assertThat(codec.read(shop)).isEqualTo(new ApiSecret("new", List.of(new ApiHeader("X-Shop-Token", "t-1"))));
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(2)).record(events.capture());
        assertThat(events.getAllValues()).extracting(AuditEvent::action)
                .containsExactly(AuditAction.EXTERNAL_API_UPDATED, AuditAction.EXTERNAL_API_SECRET_CHANGED);
        assertThat(events.getAllValues().toString()).doesNotContain("new").doesNotContain("old");
    }

    @Test
    void updateApi_EmptySecretRemovesItAndSecretHeadersReplaceAll() {
        var shop = shop();
        shop.setSecret(codec.write(new ApiSecret("old", List.of(new ApiHeader("X-Shop-Token", "t-1")))));
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        service.updateApi(7L, new UpdateExternalApiCommand().secret("").secretHeaders(List.of(new ApiHeaderDto("X-Other", "o-1"))));

        assertThat(codec.read(shop)).isEqualTo(new ApiSecret(null, List.of(new ApiHeader("X-Other", "o-1"))));
    }

    @Test
    void updateApi_RemovingEverySecretLeavesNothingStored() {
        var shop = shop();
        shop.setSecret(codec.write(new ApiSecret("old", List.of(new ApiHeader("X-Shop-Token", "t-1")))));
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        service.updateApi(7L, new UpdateExternalApiCommand().secret("").secretHeaders(List.of()));

        assertThat(shop.getSecret()).isNull();
    }

    @Test
    void updateApi_SwitchingTheAuthKindNeedsItsSettings() {
        var shop = shop();
        shop.setSecret(codec.write(new ApiSecret("pw", List.of())));
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> service.updateApi(7L, new UpdateExternalApiCommand().authKind(ApiAuthType.BASIC)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("username");
        verify(externalApiRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateApi_EmptyStringsRemoveDescriptionAndDocsUrl() {
        var shop = shop();
        shop.setDescription("Orders");
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        service.updateApi(7L, new UpdateExternalApiCommand().description("").docsUrl(""));

        assertThat(shop.getDescription()).isNull();
        assertThat(shop.getDocsUrl()).isNull();
    }

    @Test
    void updateApi_DisablingStopsTheNextCall() {
        var shop = shop();
        shop.setNextCallAt(NOW.plusSeconds(60));
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        service.updateApi(7L, new UpdateExternalApiCommand().enabled(false));

        assertThat(shop.isEnabled()).isFalse();
        assertThat(shop.getNextCallAt()).isNull();
    }

    @Test
    void deleteApi_DeletesAndAudits() {
        var shop = shop();
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        service.deleteApi(7L);

        verify(externalApiRepository).delete(shop);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.EXTERNAL_API_DELETED);
    }

    @Test
    void deleteApi_NotFound() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteApi(7L)).isInstanceOf(ExternalApiNotFoundException.class);
        verify(externalApiRepository, never()).delete(any());
    }

    @Test
    void findCalls_ListsTheAppsLatestCalls() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop()));
        var orders = ExternalApiEndpoint.builder().id(new ExternalApiEndpoint.ExternalApiEndpointId(30L)).name("Orders")
                .path(new ApiPath("/orders")).build();
        when(externalApiCallRepository.findTop50ByEndpoint_Api_IdOrderByStartedAtDesc(SHOP_ID)).thenReturn(List.of(
                ExternalApiCall.builder().id(5L).endpoint(orders).startedAt(NOW).outcome(CallOutcome.UNCHANGED).build()));

        assertThat(service.findCalls(7L)).singleElement().satisfies(dto -> {
            assertThat(dto.getEndpointName()).isEqualTo("Orders");
            assertThat(dto.getOutcome()).isEqualTo(ApiCallResult.UNCHANGED);
        });
    }

    @Test
    void findCalls_NotFoundForAnotherUsersApp() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCalls(7L)).isInstanceOf(ExternalApiNotFoundException.class);
        verify(externalApiCallRepository, never()).findTop50ByEndpoint_Api_IdOrderByStartedAtDesc(any());
    }

    @Test
    void callNow_MakesTheNextCallDueNow() {
        var shop = shop();
        shop.setNextCallAt(NOW.plusSeconds(3600));
        shop.setStatus(ApiStatus.FAILING);
        shop.setFailureCount(4);
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        var dto = service.callNow(7L);

        assertThat(shop.getNextCallAt()).isEqualTo(NOW);
        assertThat(shop.getStatus()).isEqualTo(ApiStatus.FAILING);
        assertThat(shop.getFailureCount()).isEqualTo(4);
        assertThat(dto.getNextCallAt()).isNotNull();
        verify(externalApiRepository).saveAndFlush(shop);
    }

    @Test
    void callNow_ResumesAPausedAppOrOneThatNeedsSetup() {
        for (ApiStatus status : List.of(ApiStatus.PAUSED, ApiStatus.NEEDS_SETUP)) {
            var shop = shop();
            shop.setStatus(status);
            shop.setFailureCount(10);
            when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

            service.callNow(7L);

            assertThat(shop.getStatus()).isEqualTo(ApiStatus.ACTIVE);
            assertThat(shop.getFailureCount()).isZero();
            assertThat(shop.getNextCallAt()).isEqualTo(NOW);
        }
    }

    @Test
    void callNow_TurnedOffAppRejected() {
        var shop = shop();
        shop.setEnabled(false);
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> service.callNow(7L)).isInstanceOf(ExternalApiDisabledException.class);
        verify(externalApiRepository, never()).saveAndFlush(any());
    }

    @Test
    void findEndpoints_ShowEachEndpointsActiveMappingBeforeAProposedOrSwitchedOffOne() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop()));
        var orders = ExternalApiEndpoint.builder().id(new ExternalApiEndpoint.ExternalApiEndpointId(30L)).name("orders").path(new ApiPath("/orders")).build();
        var people = ExternalApiEndpoint.builder().id(new ExternalApiEndpoint.ExternalApiEndpointId(31L)).name("people").path(new ApiPath("/people")).build();
        when(externalApiEndpointRepository.findByApi_IdOrderByNameAsc(SHOP_ID)).thenReturn(List.of(orders, people));
        var collection = Collection.builder().id(new Collection.CollectionId(4L)).build();
        when(externalApiMappingRepository.findByEndpoint_Id(orders.getId())).thenReturn(List.of(
                ExternalApiMapping.builder().id(new ExternalApiMapping.ExternalApiMappingId(40L)).collection(collection).state(MappingState.OFF).build(),
                ExternalApiMapping.builder().id(new ExternalApiMapping.ExternalApiMappingId(41L)).collection(collection).state(MappingState.ACTIVE).build(),
                ExternalApiMapping.builder().id(new ExternalApiMapping.ExternalApiMappingId(42L)).collection(collection).state(MappingState.PROPOSED).build()));
        when(externalApiMappingRepository.findByEndpoint_Id(people.getId())).thenReturn(List.of());

        var endpoints = service.findEndpoints(7L);

        assertThat(endpoints.get(0).getMapping().getId()).isEqualTo(41L);
        assertThat(endpoints.get(1).getMapping()).isNull();
    }

    @Test
    void findEndpoints_ListsTheAppsEndpoints() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop()));
        when(externalApiEndpointRepository.findByApi_IdOrderByNameAsc(SHOP_ID))
                .thenReturn(List.of(ExternalApiEndpoint.builder().name("orders").path(new ApiPath("/orders")).build()));

        assertThat(service.findEndpoints(7L)).singleElement().satisfies(dto -> assertThat(dto.getName()).isEqualTo("orders"));
    }

    @Test
    void createEndpoint_StoresAUserEndpoint() {
        var shop = shop();
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        var dto = service.createEndpoint(7L, new NewExternalApiEndpointCommand("orders", "/orders")
                .query(Map.of("status", "open")).itemsPath("$.data[*]").itemKeyPath("$.id").description("Open orders"));

        ArgumentCaptor<ExternalApiEndpoint> saved = ArgumentCaptor.forClass(ExternalApiEndpoint.class);
        verify(externalApiEndpointRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getApi()).isSameAs(shop);
        assertThat(saved.getValue().getMethod()).isEqualTo(ApiHttpMethod.GET);
        assertThat(saved.getValue().getPath()).isEqualTo(new ApiPath("/orders"));
        assertThat(saved.getValue().getQuery()).containsEntry("status", "open");
        assertThat(saved.getValue().getItemsPath()).isEqualTo(new JsonPathExpr("$.data[*]"));
        assertThat(saved.getValue().getSource()).isEqualTo(EndpointSource.USER);
        assertThat(shop.getEndpoints()).containsExactly(saved.getValue());
        assertThat(dto.getId()).isEqualTo(30L);
    }

    @Test
    void createEndpoint_DuplicateNameRejected() {
        var shop = shop();
        shop.getEndpoints().add(ExternalApiEndpoint.builder().api(shop).name("Orders").path(new ApiPath("/orders")).build());
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> service.createEndpoint(7L, new NewExternalApiEndpointCommand("orders", "/v2/orders")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(externalApiEndpointRepository, never()).saveAndFlush(any());
    }

    @Test
    void createEndpoint_BodyOnlyForPostAndPathsStayBelowTheBaseUrl() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop()));

        assertThatThrownBy(() -> service.createEndpoint(7L, new NewExternalApiEndpointCommand("orders", "/orders")
                .body(Map.of("q", "x")))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("POST");
        assertThatThrownBy(() -> service.createEndpoint(7L, new NewExternalApiEndpointCommand("admin", "/../admin")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createEndpoint(7L, new NewExternalApiEndpointCommand("q", "/orders?x=1")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(externalApiEndpointRepository, never()).saveAndFlush(any());
    }

    @Test
    void createEndpoint_PostReadQueryWithoutBodySendsAnEmptyObject() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop()));

        service.createEndpoint(7L, new NewExternalApiEndpointCommand("search", "/search").method(EndpointMethod.POST));

        ArgumentCaptor<ExternalApiEndpoint> saved = ArgumentCaptor.forClass(ExternalApiEndpoint.class);
        verify(externalApiEndpointRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getMethod()).isEqualTo(ApiHttpMethod.POST);
        assertThat(saved.getValue().getBody()).isEmpty();
    }

    @Test
    void updateEndpoint_ChangesGivenFieldsAndEmptyStringsRemovePaths() {
        var shop = shop();
        var orders = endpoint(shop, "orders");
        orders.setItemsPath(new JsonPathExpr("$.data[*]"));
        orders.setDescription("Open orders");
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));
        when(externalApiEndpointRepository.findByIdAndApi_Id(orders.getId(), SHOP_ID)).thenReturn(Optional.of(orders));

        service.updateEndpoint(7L, 30L, new UpdateExternalApiEndpointCommand().path("/v2/orders").itemsPath("").description("")
                .enabled(false));

        assertThat(orders.getPath()).isEqualTo(new ApiPath("/v2/orders"));
        assertThat(orders.getItemsPath()).isNull();
        assertThat(orders.getDescription()).isNull();
        assertThat(orders.isEnabled()).isFalse();
        assertThat(orders.getName()).isEqualTo("orders");
        verify(externalApiEndpointRepository).saveAndFlush(orders);
    }

    @Test
    void updateEndpoint_SwitchingToGetDropsTheBody() {
        var shop = shop();
        var search = endpoint(shop, "search");
        search.setMethod(ApiHttpMethod.POST);
        search.setBody(Map.of("q", "x"));
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));
        when(externalApiEndpointRepository.findByIdAndApi_Id(search.getId(), SHOP_ID)).thenReturn(Optional.of(search));

        service.updateEndpoint(7L, 30L, new UpdateExternalApiEndpointCommand().method(EndpointMethod.GET));

        assertThat(search.getMethod()).isEqualTo(ApiHttpMethod.GET);
        assertThat(search.getBody()).isNull();
    }

    @Test
    void updateEndpoint_RenamingToAnotherEndpointsNameRejected() {
        var shop = shop();
        var orders = endpoint(shop, "orders");
        shop.getEndpoints().add(ExternalApiEndpoint.builder().id(new ExternalApiEndpoint.ExternalApiEndpointId(31L)).api(shop)
                .name("customers").path(new ApiPath("/customers")).build());
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));
        when(externalApiEndpointRepository.findByIdAndApi_Id(orders.getId(), SHOP_ID)).thenReturn(Optional.of(orders));

        assertThatThrownBy(() -> service.updateEndpoint(7L, 30L, new UpdateExternalApiEndpointCommand().name("Customers")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(externalApiEndpointRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateEndpoint_NotFoundInThisApp() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop()));
        when(externalApiEndpointRepository.findByIdAndApi_Id(new ExternalApiEndpoint.ExternalApiEndpointId(99L), SHOP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEndpoint(7L, 99L, new UpdateExternalApiEndpointCommand()))
                .isInstanceOf(ExternalApiEndpointNotFoundException.class);
    }

    @Test
    void deleteEndpoint_RemovesItFromTheApp() {
        var shop = shop();
        var orders = endpoint(shop, "orders");
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));
        when(externalApiEndpointRepository.findByIdAndApi_Id(orders.getId(), SHOP_ID)).thenReturn(Optional.of(orders));

        service.deleteEndpoint(7L, 30L);

        assertThat(shop.getEndpoints()).isEmpty();
        verify(externalApiEndpointRepository).delete(orders);
    }

    @Test
    void testCall_CallsWithTheGivenSettingsAndStoresNothing() {
        when(externalApiClient.call(any(), any())).thenReturn(new ApiResponse(200, "application/json", "[{\"id\":1}]", false, null));

        var result = service.testCall(new TestExternalApiCommand("https://api.shop.example/v1", "/orders")
                .authKind(ApiAuthType.API_KEY_QUERY).authConfig(new ApiAuthConfigDto().queryParam("api_key")).secret("key-123")
                .query(Map.of("status", "open")));

        ArgumentCaptor<ExternalApi> api = ArgumentCaptor.forClass(ExternalApi.class);
        ArgumentCaptor<ExternalApiEndpoint> endpoint = ArgumentCaptor.forClass(ExternalApiEndpoint.class);
        verify(externalApiClient).call(api.capture(), endpoint.capture());
        assertThat(api.getValue().getBaseUrl()).isEqualTo(new BaseUrl("https://api.shop.example/v1"));
        assertThat(api.getValue().getAuthConfig()).isEqualTo(new ApiAuthConfig(null, "api_key", null, null, null, null));
        assertThat(codec.read(api.getValue()).value()).isEqualTo("key-123");
        assertThat(endpoint.getValue().getPath()).isEqualTo(new ApiPath("/orders"));
        assertThat(endpoint.getValue().getQuery()).containsEntry("status", "open");
        assertThat(result.getOk()).isTrue();
        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getContentType()).isEqualTo("application/json");
        assertThat(result.getBody()).isEqualTo("[{\"id\":1}]");
        assertThat(result.getBodyTruncated()).isFalse();
        assertThat(result.getError()).isNull();
        verify(externalApiRepository, never()).saveAndFlush(any());
        verify(externalApiEndpointRepository, never()).saveAndFlush(any());
    }

    @Test
    void testCall_UsesTheStoredSecretOfTheAppWhenNoneIsGiven() {
        var shop = shop();
        shop.setSecret(codec.write(new ApiSecret("stored-key", List.of(new ApiHeader("X-Shop-Token", "t-1")))));
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.of(shop));
        when(externalApiClient.call(any(), any())).thenReturn(new ApiResponse(200, null, "", false, null));

        service.testCall(new TestExternalApiCommand("https://api.shop.example", "/orders").apiId(7L)
                .authKind(ApiAuthType.BEARER));

        ArgumentCaptor<ExternalApi> api = ArgumentCaptor.forClass(ExternalApi.class);
        verify(externalApiClient).call(api.capture(), any());
        assertThat(codec.read(api.getValue())).isEqualTo(new ApiSecret("stored-key", List.of(new ApiHeader("X-Shop-Token", "t-1"))));
        assertThat(api.getValue()).isNotSameAs(shop);
    }

    @Test
    void testCall_AnotherUsersAppIsNotFound() {
        when(externalApiRepository.findByIdAndOwner_Id(SHOP_ID, OWNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testCall(new TestExternalApiCommand("https://api.shop.example", "/orders").apiId(7L)))
                .isInstanceOf(ExternalApiNotFoundException.class);
        verify(externalApiClient, never()).call(any(), any());
    }

    @Test
    void testCall_CutsLongAnswers() {
        when(externalApiClient.call(any(), any())).thenReturn(new ApiResponse(200, "text/plain", "x".repeat(25_000), false, null));

        var result = service.testCall(new TestExternalApiCommand("https://api.shop.example", "/orders"));

        assertThat(result.getBody()).hasSize(ExternalApiService.TEST_BODY_CHARS);
        assertThat(result.getBodyTruncated()).isTrue();
    }

    @Test
    void testCall_ErrorStatusIsNotOk() {
        when(externalApiClient.call(any(), any())).thenReturn(new ApiResponse(404, "text/html", "Not found", true, null));

        var result = service.testCall(new TestExternalApiCommand("https://api.shop.example", "/orders"));

        assertThat(result.getOk()).isFalse();
        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getBodyTruncated()).isTrue();
    }

    @Test
    void testCall_RefusedOrFailedCallsAreFailedResults() {
        when(externalApiClient.call(any(), any()))
                .thenThrow(new OutboundRequestException("intranet.example does not resolve to public addresses only"))
                .thenThrow(new ApiAuthException("The token endpoint auth.example rejected the client credentials (401)", true));

        var refused = service.testCall(new TestExternalApiCommand("https://intranet.example", "/orders"));
        var rejected = service.testCall(new TestExternalApiCommand("https://api.shop.example", "/orders"));

        assertThat(refused.getOk()).isFalse();
        assertThat(refused.getStatus()).isNull();
        assertThat(refused.getError()).isEqualTo("intranet.example does not resolve to public addresses only");
        assertThat(rejected.getError()).contains("rejected the client credentials");
    }

    @Test
    void testCall_InvalidSettingsRejected() {
        assertThatThrownBy(() -> service.testCall(new TestExternalApiCommand("http://api.shop.example", "/orders")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.testCall(new TestExternalApiCommand("https://api.shop.example", "/orders")
                .body(Map.of("q", "x")))).isInstanceOf(IllegalArgumentException.class);
        verify(externalApiClient, never()).call(any(), any());
    }

    private ExternalApi savedApi() {
        ArgumentCaptor<ExternalApi> saved = ArgumentCaptor.forClass(ExternalApi.class);
        verify(externalApiRepository).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private static ExternalApi shop() {
        var shop = ExternalApi.builder().id(SHOP_ID).owner(AppUser.builder().id(OWNER).build()).name("Shop").slug(new Slug("shop"))
                .baseUrl(new BaseUrl("https://api.shop.example")).endpoints(new ArrayList<>()).build();
        shop.setCreatedAt(NOW);
        return shop;
    }

    private static ExternalApiEndpoint endpoint(ExternalApi api, String name) {
        var endpoint = ExternalApiEndpoint.builder().id(new ExternalApiEndpoint.ExternalApiEndpointId(30L)).api(api).name(name)
                .path(new ApiPath("/" + name)).build();
        api.getEndpoints().add(endpoint);
        return endpoint;
    }
}
