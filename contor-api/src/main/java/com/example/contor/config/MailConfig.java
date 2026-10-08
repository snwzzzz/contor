package com.example.contor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MsGraphProperties.class, WebProperties.class})
@EnableScheduling
public class MailConfig {
}
