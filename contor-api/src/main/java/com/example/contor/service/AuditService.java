package com.example.contor.service;

import com.example.contor.domain.AuditLog;
import com.example.contor.persistence.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/** Appends to the audit log. Joins the caller's transaction, so an entry is only kept if the audited change commits. */
@RequiredArgsConstructor
@Service
public class AuditService {

    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(AuditEvent event) {
        HttpServletRequest request = currentRequest();
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType(event.actorType())
                .actorUserId(event.actorUserId())
                .action(event.action().name())
                .entityType(event.entityType())
                .collectionId(event.collectionId())
                .entityId(event.entityId())
                .before(event.before())
                .after(event.after())
                .ipAddress(request != null ? request.getRemoteAddr() : null)
                .userAgent(request != null ? truncate(request.getHeader(HttpHeaders.USER_AGENT)) : null)
                .build());
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_USER_AGENT_LENGTH ? value : value.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
