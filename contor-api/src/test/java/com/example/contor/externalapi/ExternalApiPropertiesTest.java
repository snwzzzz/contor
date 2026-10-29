package com.example.contor.externalapi;

import com.example.contor.config.ExternalApiConfig;
import com.example.contor.config.ExternalApiProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ExternalApiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ExternalApiConfig.class);

    @Test
    void defaults_to_polling_every_minute_with_the_planned_caps() {
        runner.run(context -> {
            var properties = context.getBean(ExternalApiProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.pollTick()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.maxResponseSize()).isEqualTo(DataSize.ofMegabytes(1));
            assertThat(properties.maxConcurrent()).isEqualTo(4);
            assertThat(properties.historyDays()).isEqualTo(30);
            assertThat(properties.snapshotsKept()).isEqualTo(5);
        });
    }

    @Test
    void binds_overrides() {
        runner.withPropertyValues("app.external-apis.enabled=false", "app.external-apis.timeout=3s",
                        "app.external-apis.max-response-size=256KB", "app.external-apis.max-concurrent=2")
                .run(context -> {
                    var properties = context.getBean(ExternalApiProperties.class);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(properties.maxResponseSize()).isEqualTo(DataSize.ofKilobytes(256));
                    assertThat(properties.maxConcurrent()).isEqualTo(2);
                });
    }
}
