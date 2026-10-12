package com.example.contor.config;

import com.example.contor.ai.AiExecutors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AI wiring: properties (Ollama must be on localhost) and the background executors. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AiProperties.class, MailContextProperties.class})
public class AiConfig {

    @Bean(destroyMethod = "close")
    public AiExecutors aiExecutors() {
        return AiExecutors.create();
    }
}
