package com.example.contor.controller;

import com.example.contor.ControllerTest;
import com.example.contor.TestAuth;
import com.example.contor.api.model.ApiAuthConfigDto;
import com.example.contor.api.model.ApiAuthType;
import com.example.contor.api.model.ApiCallResult;
import com.example.contor.api.model.ApiState;
import com.example.contor.api.model.ApiTestResultDto;
import com.example.contor.api.model.EndpointMethod;
import com.example.contor.api.model.EndpointOrigin;
import com.example.contor.api.model.ExternalApiCallDto;
import com.example.contor.api.model.ExternalApiDto;
import com.example.contor.api.model.ExternalApiEndpointDto;
import com.example.contor.exception.DuplicateResourceException;
import com.example.contor.exception.ExternalApiDisabledException;
import com.example.contor.exception.ExternalApiEndpointNotFoundException;
import com.example.contor.exception.ExternalApiNotFoundException;
import com.example.contor.service.ExternalApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(ExternalApiController.class)
public class ExternalApiControllerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 29, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExternalApiService externalApiService;

    @Test
    void list_apps_returns_200() throws Exception {
        when(externalApiService.findAll()).thenReturn(List.of(shop()));

        mockMvc.perform(get("/api/external-apis").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].name").value("Shop"))
                .andExpect(jsonPath("$[0].authKind").value("API_KEY_HEADER"))
                .andExpect(jsonPath("$[0].hasSecret").value(true))
                .andExpect(jsonPath("$[0].secretHeaderNames[0]").value("X-Shop-Token"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    @Test
    void list_apps_without_login_returns_401() throws Exception {
        mockMvc.perform(get("/api/external-apis")).andExpect(status().isUnauthorized());
    }

    @Test
    void create_app_returns_201_and_passes_the_secret_on() throws Exception {
        when(externalApiService.createApi(any())).thenReturn(shop());

        mockMvc.perform(post("/api/external-apis").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Shop", "baseUrl": "https://api.shop.example/v1", "authKind": "API_KEY_HEADER",
                                 "authConfig": {"headerName": "X-Api-Key"}, "secret": "key-123",
                                 "secretHeaders": [{"name": "X-Shop-Token", "value": "t-1"}], "pollIntervalMinutes": 60}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(content().string(not(containsString("key-123"))))
                .andExpect(content().string(not(containsString("t-1"))));
        verify(externalApiService).createApi(argThat(command -> command.getSecret().equals("key-123")
                && command.getAuthKind() == ApiAuthType.API_KEY_HEADER && command.getPollIntervalMinutes() == 60
                && command.getSecretHeaders().getFirst().getValue().equals("t-1")));
    }

    @Test
    void create_app_without_name_or_with_a_too_short_interval_returns_400() throws Exception {
        mockMvc.perform(post("/api/external-apis").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseUrl\": \"https://api.shop.example\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/external-apis").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Shop\", \"baseUrl\": \"https://api.shop.example\", \"pollIntervalMinutes\": 5}"))
                .andExpect(status().isBadRequest());
        verify(externalApiService, never()).createApi(any());
    }

    @Test
    void create_app_with_invalid_settings_returns_400_with_the_reason() throws Exception {
        when(externalApiService.createApi(any())).thenThrow(new IllegalArgumentException("The token is missing"));

        mockMvc.perform(post("/api/external-apis").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Shop\", \"baseUrl\": \"https://api.shop.example\", \"authKind\": \"BEARER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The token is missing"));
    }

    @Test
    void create_app_without_csrf_returns_403() throws Exception {
        mockMvc.perform(post("/api/external-apis").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Shop\", \"baseUrl\": \"https://api.shop.example\"}"))
                .andExpect(status().isForbidden());
        verify(externalApiService, never()).createApi(any());
    }

    @Test
    void get_app_returns_200_or_404() throws Exception {
        when(externalApiService.findById(7L)).thenReturn(shop());
        when(externalApiService.findById(8L)).thenThrow(new ExternalApiNotFoundException(8L));

        mockMvc.perform(get("/api/external-apis/7").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("https://api.shop.example/v1"));
        mockMvc.perform(get("/api/external-apis/8").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("App not found with id: 8"));
    }

    @Test
    void update_app_returns_200() throws Exception {
        when(externalApiService.updateApi(eq(7L), any())).thenReturn(shop());

        mockMvc.perform(patch("/api/external-apis/7").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"secret\": \"\", \"enabled\": false}"))
                .andExpect(status().isOk());
        verify(externalApiService).updateApi(eq(7L), argThat(command -> command.getSecret().isEmpty()
                && Boolean.FALSE.equals(command.getEnabled()) && command.getName() == null && command.getSecretHeaders() == null));
    }

    @Test
    void delete_app_returns_204() throws Exception {
        mockMvc.perform(delete("/api/external-apis/7").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(externalApiService).deleteApi(7L);
    }

    @Test
    void test_call_returns_200_with_the_result() throws Exception {
        when(externalApiService.testCall(any())).thenReturn(new ApiTestResultDto(false, false, 12)
                .error("intranet.example does not resolve to public addresses only"));

        mockMvc.perform(post("/api/external-apis/test").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\": \"https://intranet.example\", \"path\": \"/orders\", \"apiId\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").value("intranet.example does not resolve to public addresses only"));
        verify(externalApiService).testCall(argThat(command -> command.getApiId() == 7L && command.getPath().equals("/orders")));
    }

    @Test
    void test_call_without_path_returns_400() throws Exception {
        mockMvc.perform(post("/api/external-apis/test").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseUrl\": \"https://api.shop.example\"}"))
                .andExpect(status().isBadRequest());
        verify(externalApiService, never()).testCall(any());
    }

    @Test
    void list_endpoints_returns_200() throws Exception {
        when(externalApiService.findEndpoints(7L)).thenReturn(List.of(orders()));

        mockMvc.perform(get("/api/external-apis/7/endpoints").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("orders"))
                .andExpect(jsonPath("$[0].method").value("GET"))
                .andExpect(jsonPath("$[0].query.status").value("open"))
                .andExpect(jsonPath("$[0].source").value("USER"));
    }

    @Test
    void create_endpoint_returns_201_or_409_for_a_taken_name() throws Exception {
        when(externalApiService.createEndpoint(eq(7L), argThat(command -> command.getName().equals("orders")))).thenReturn(orders());
        when(externalApiService.createEndpoint(eq(7L), argThat(command -> command.getName().equals("customers"))))
                .thenThrow(new DuplicateResourceException("The app already has an endpoint named customers"));

        mockMvc.perform(post("/api/external-apis/7/endpoints").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"orders\", \"path\": \"/orders\", \"query\": {\"status\": \"open\"}, \"method\": \"GET\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30));
        mockMvc.perform(post("/api/external-apis/7/endpoints").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"customers\", \"path\": \"/customers\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_endpoint_returns_200_or_404() throws Exception {
        when(externalApiService.updateEndpoint(eq(7L), eq(30L), any())).thenReturn(orders());
        when(externalApiService.updateEndpoint(eq(7L), eq(99L), any())).thenThrow(new ExternalApiEndpointNotFoundException(99L));

        mockMvc.perform(patch("/api/external-apis/7/endpoints/30").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\": \"POST\", \"body\": {\"query\": \"x\"}}"))
                .andExpect(status().isOk());
        verify(externalApiService).updateEndpoint(eq(7L), eq(30L), argThat(command -> command.getMethod() == EndpointMethod.POST
                && Map.of("query", "x").equals(command.getBody())));
        mockMvc.perform(patch("/api/external-apis/7/endpoints/99").with(TestAuth.user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_endpoint_returns_204() throws Exception {
        mockMvc.perform(delete("/api/external-apis/7/endpoints/30").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(externalApiService).deleteEndpoint(7L, 30L);
    }

    @Test
    void list_calls_returns_200() throws Exception {
        when(externalApiService.findCalls(7L)).thenReturn(List.of(new ExternalApiCallDto(5L, 30L, "Orders", NOW, 120, ApiCallResult.FAILED, 0, 0, 0)
                .httpStatus(500).error("The app answered with HTTP 500")));

        mockMvc.perform(get("/api/external-apis/7/calls").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endpointName").value("Orders"))
                .andExpect(jsonPath("$[0].outcome").value("FAILED"))
                .andExpect(jsonPath("$[0].httpStatus").value(500))
                .andExpect(jsonPath("$[0].error").value("The app answered with HTTP 500"));
    }

    @Test
    void list_calls_of_another_users_app_returns_404() throws Exception {
        when(externalApiService.findCalls(8L)).thenThrow(new ExternalApiNotFoundException(8L));

        mockMvc.perform(get("/api/external-apis/8/calls").with(TestAuth.user())).andExpect(status().isNotFound());
    }

    @Test
    void call_now_returns_202_with_the_app() throws Exception {
        when(externalApiService.callNow(7L)).thenReturn(shop().nextCallAt(NOW));

        mockMvc.perform(post("/api/external-apis/7/calls").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nextCallAt").exists());
    }

    @Test
    void call_now_for_a_turned_off_app_returns_409() throws Exception {
        when(externalApiService.callNow(7L)).thenThrow(new ExternalApiDisabledException("Shop"));

        mockMvc.perform(post("/api/external-apis/7/calls").with(TestAuth.user()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shop is turned off; turn it on to call it"));
    }

    @Test
    void call_now_needs_login_and_csrf() throws Exception {
        mockMvc.perform(post("/api/external-apis/7/calls").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/external-apis/7/calls").with(TestAuth.user())).andExpect(status().isForbidden());
        verify(externalApiService, never()).callNow(any());
    }

    private static ExternalApiDto shop() {
        return new ExternalApiDto(7L, "Shop", "shop", "https://api.shop.example/v1", ApiAuthType.API_KEY_HEADER,
                new ApiAuthConfigDto().headerName("X-Api-Key"), true, List.of(), List.of("X-Shop-Token"), 360, true, true,
                ApiState.ACTIVE, 0, 200, 1, NOW);
    }

    private static ExternalApiEndpointDto orders() {
        return new ExternalApiEndpointDto(30L, 7L, "orders", EndpointMethod.GET, "/orders", Map.of("status", "open"),
                EndpointOrigin.USER, true, NOW);
    }
}
