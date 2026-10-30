package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * How contor calls the users' external APIs ({@code app.external-apis.*}, ADR 0012).
 *
 * @param enabled         the scheduled polls run (off in tests and CI)
 * @param pollTick        how often due apps are looked for
 * @param timeout         per call
 * @param maxResponseSize answers are cut here
 * @param maxConcurrent   apps called at the same time (one host at a time)
 * @param historyDays     how long the call history is kept
 * @param snapshotsKept   answers kept per endpoint
 */
@ConfigurationProperties("app.external-apis")
public record ExternalApiProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1m") Duration pollTick,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("1MB") DataSize maxResponseSize,
        @DefaultValue("4") int maxConcurrent,
        @DefaultValue("30") int historyDays,
        @DefaultValue("5") int snapshotsKept) {
}
