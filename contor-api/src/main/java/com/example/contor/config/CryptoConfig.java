package com.example.contor.config;

import com.example.contor.security.FieldEncryptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EncryptionProperties.class)
public class CryptoConfig {

    /** Fails application startup when the key is missing or invalid. */
    @Bean
    public FieldEncryptor fieldEncryptor(EncryptionProperties properties) {
        return new FieldEncryptor(properties);
    }
}
