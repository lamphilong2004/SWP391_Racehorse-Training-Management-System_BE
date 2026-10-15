package com.racehorse.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Allowed browser origins, read from configuration (app.cors.allowed-origins / env CORS_ALLOWED_ORIGINS). */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
