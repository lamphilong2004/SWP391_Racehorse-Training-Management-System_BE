package com.racehorse.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings (app.jwt.*). The HS256 secret comes from the JWT_SECRET environment variable and is never committed;
 * the application refuses to start without a key of at least 32 bytes.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiresIn, String issuer) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret (env JWT_SECRET) must be set to at least "
                    + MIN_SECRET_BYTES + " bytes.");
        }
        if (expiresIn == null || expiresIn.isNegative() || expiresIn.isZero()) {
            throw new IllegalStateException("app.jwt.expires-in must be a positive duration.");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "racehorse-backend";
        }
    }

    /** Never prints the secret. */
    @Override
    public String toString() {
        return "JwtProperties{expiresIn=" + expiresIn + ", issuer=" + issuer + "}";
    }
}
