package com.example.contor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Sets {@code created_at} on insert and {@code last_modified} on insert and update (both {@code timestamptz}). */
@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_modified")
    private Instant lastModified;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        lastModified = now;
    }

    @PreUpdate
    protected void onUpdate() {
        lastModified = Instant.now();
    }
}
