package com.example.contor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Public URL of the web app (contor-web), for sending the browser back after flows that pass through the backend. */
@ConfigurationProperties("app.web")
public record WebProperties(@DefaultValue("http://localhost:3000") String url) {
}
