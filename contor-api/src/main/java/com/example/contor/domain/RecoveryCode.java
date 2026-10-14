package com.example.contor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Hashed one-time TOTP recovery code. Detail table of {@link AppUser}: plain identity ID, no auditing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "\"recovery_code\"")
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;
}
