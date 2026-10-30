package com.example.contor.controller;

import com.example.contor.api.ExternalApisApi;
import com.example.contor.api.model.ApiDiscoveryDto;
import com.example.contor.api.model.ApiTestResultDto;
import com.example.contor.api.model.ExternalApiCallDto;
import com.example.contor.api.model.ExternalApiDto;
import com.example.contor.api.model.ExternalApiEndpointDataDto;
import com.example.contor.api.model.ExternalApiEndpointDto;
import com.example.contor.api.model.NewApiDiscoveryCommand;
import com.example.contor.api.model.NewExternalApiCommand;
import com.example.contor.api.model.NewExternalApiEndpointCommand;
import com.example.contor.api.model.TestExternalApiCommand;
import com.example.contor.api.model.UpdateExternalApiCommand;
import com.example.contor.api.model.UpdateExternalApiEndpointCommand;
import com.example.contor.service.ExternalApiDiscoveryService;
import com.example.contor.service.ExternalApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class ExternalApiController implements ExternalApisApi {

    private final ExternalApiService externalApiService;
    private final ExternalApiDiscoveryService externalApiDiscoveryService;

    @Override
    public ResponseEntity<List<ExternalApiDto>> listExternalApis() {
        return ResponseEntity.ok(externalApiService.findAll());
    }

    @Override
    public ResponseEntity<ExternalApiDto> createExternalApi(NewExternalApiCommand newExternalApiCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(externalApiService.createApi(newExternalApiCommand));
    }

    @Override
    public ResponseEntity<ApiTestResultDto> testExternalApi(TestExternalApiCommand testExternalApiCommand) {
        return ResponseEntity.ok(externalApiService.testCall(testExternalApiCommand));
    }

    @Override
    public ResponseEntity<ApiDiscoveryDto> startApiDiscovery(NewApiDiscoveryCommand newApiDiscoveryCommand) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(externalApiDiscoveryService.start(newApiDiscoveryCommand));
    }

    @Override
    public ResponseEntity<ApiDiscoveryDto> getApiDiscovery(String discoveryId) {
        return ResponseEntity.ok(externalApiDiscoveryService.find(discoveryId));
    }

    @Override
    public ResponseEntity<ExternalApiDto> getExternalApi(Long apiId) {
        return ResponseEntity.ok(externalApiService.findById(apiId));
    }

    @Override
    public ResponseEntity<ExternalApiDto> updateExternalApi(Long apiId, UpdateExternalApiCommand updateExternalApiCommand) {
        return ResponseEntity.ok(externalApiService.updateApi(apiId, updateExternalApiCommand));
    }

    @Override
    public ResponseEntity<Void> deleteExternalApi(Long apiId) {
        externalApiService.deleteApi(apiId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<ExternalApiCallDto>> listExternalApiCalls(Long apiId) {
        return ResponseEntity.ok(externalApiService.findCalls(apiId));
    }

    @Override
    public ResponseEntity<ExternalApiDto> callExternalApiNow(Long apiId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(externalApiService.callNow(apiId));
    }

    @Override
    public ResponseEntity<List<ExternalApiEndpointDto>> listExternalApiEndpoints(Long apiId) {
        return ResponseEntity.ok(externalApiService.findEndpoints(apiId));
    }

    @Override
    public ResponseEntity<ExternalApiEndpointDto> createExternalApiEndpoint(Long apiId, NewExternalApiEndpointCommand newExternalApiEndpointCommand) {
        return ResponseEntity.status(HttpStatus.CREATED).body(externalApiService.createEndpoint(apiId, newExternalApiEndpointCommand));
    }

    @Override
    public ResponseEntity<ExternalApiEndpointDto> updateExternalApiEndpoint(Long apiId, Long endpointId,
                                                                            UpdateExternalApiEndpointCommand updateExternalApiEndpointCommand) {
        return ResponseEntity.ok(externalApiService.updateEndpoint(apiId, endpointId, updateExternalApiEndpointCommand));
    }

    @Override
    public ResponseEntity<ExternalApiEndpointDataDto> getExternalApiEndpointData(Long apiId, Long endpointId) {
        return ResponseEntity.ok(externalApiService.findEndpointData(apiId, endpointId));
    }

    @Override
    public ResponseEntity<Void> deleteExternalApiEndpoint(Long apiId, Long endpointId) {
        externalApiService.deleteEndpoint(apiId, endpointId);
        return ResponseEntity.noContent().build();
    }
}
