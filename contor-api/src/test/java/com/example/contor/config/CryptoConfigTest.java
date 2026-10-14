package com.example.contor.config;

import com.example.contor.security.FieldEncryptor;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class CryptoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CryptoConfig.class);

    @Test
    void starts_with_a_valid_key() {
        runner.withPropertyValues("app.encryption.key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
                .run(context -> assertThat(context).hasSingleBean(FieldEncryptor.class));
    }

    @Test
    void refuses_to_start_without_a_key() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("APP_ENCRYPTION_KEY is not set"));
    }

    @Test
    void refuses_to_start_with_an_invalid_key() {
        runner.withPropertyValues("app.encryption.key=too-short")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("APP_ENCRYPTION_KEY"));
    }
}
