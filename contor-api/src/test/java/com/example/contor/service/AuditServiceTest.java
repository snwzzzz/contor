package com.example.contor.service;

import com.example.contor.domain.ActorType;
import com.example.contor.domain.AuditAction;
import com.example.contor.domain.AuditLog;
import com.example.contor.persistence.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void record_StoresEventWithRequestOrigin() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("User-Agent", "Firefox");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.record(AuditEvent.user(AuditAction.AUTH_LOGIN_SUCCESS, 7L).entity("app_user", 7L).after(Map.of("k", "v")));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("AUTH_LOGIN_SUCCESS", log.getAction());
        assertEquals(ActorType.USER, log.getActorType());
        assertEquals(7L, log.getActorUserId());
        assertEquals("app_user", log.getEntityType());
        assertEquals(7L, log.getEntityId());
        assertEquals("v", log.getAfter().get("k"));
        assertEquals("10.1.2.3", log.getIpAddress());
        assertEquals("Firefox", log.getUserAgent());
        assertNotNull(log.getOccurredAt());
    }

    @Test
    void record_WorksOutsideOfRequests() {
        auditService.record(AuditEvent.system(AuditAction.COLLECTION_CREATED).record(3L, 9L));

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());
        assertEquals(ActorType.SYSTEM, captor.getValue().getActorType());
        assertEquals(3L, captor.getValue().getCollectionId());
        assertEquals(9L, captor.getValue().getEntityId());
        assertNull(captor.getValue().getIpAddress());
    }
}
