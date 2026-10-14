package com.example.contor.service;

import com.example.contor.domain.ActorType;
import com.example.contor.domain.AuditAction;
import com.example.contor.security.CurrentActor;

import java.util.Map;

/**
 * What to write into {@code audit_log}. Build with {@link #user}, {@link #ai} or {@link #system} and refine with
 * {@link #entity}, {@link #record}, {@link #before}, {@link #after}. Request origin (IP, user agent) is added by {@link AuditService}.
 */
public record AuditEvent(AuditAction action, ActorType actorType, Long actorUserId,
                         String entityType, Long collectionId, Long entityId,
                         Map<String, Object> before, Map<String, Object> after) {

    public static AuditEvent user(AuditAction action, Long userId) {
        return new AuditEvent(action, ActorType.USER, userId, null, null, null, null, null);
    }

    /** Attributed to the logged-in user, or to the system when nobody is logged in. */
    public static AuditEvent currentActor(AuditAction action) {
        return CurrentActor.userId().map(id -> user(action, id)).orElseGet(() -> system(action));
    }

    public static AuditEvent ai(AuditAction action) {
        return new AuditEvent(action, ActorType.AI, null, null, null, null, null, null);
    }

    public static AuditEvent system(AuditAction action) {
        return new AuditEvent(action, ActorType.SYSTEM, null, null, null, null, null, null);
    }

    public AuditEvent entity(String type, Long id) {
        return new AuditEvent(action, actorType, actorUserId, type, collectionId, id, before, after);
    }

    /** A record of a generated collection table, addressed as (collection id, record id). */
    public AuditEvent record(Long collectionId, Long recordId) {
        return new AuditEvent(action, actorType, actorUserId, "record", collectionId, recordId, before, after);
    }

    public AuditEvent before(Map<String, Object> snapshot) {
        return new AuditEvent(action, actorType, actorUserId, entityType, collectionId, entityId, snapshot, after);
    }

    public AuditEvent after(Map<String, Object> snapshot) {
        return new AuditEvent(action, actorType, actorUserId, entityType, collectionId, entityId, before, snapshot);
    }
}
