package com.example.contor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("dev")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DevAccountProperties.class)
public class DevConfig {
}
