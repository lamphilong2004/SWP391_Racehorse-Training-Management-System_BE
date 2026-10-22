package com.racehorse.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtSettings(String signingKey, String accessTokenTtlSeconds) {
}
