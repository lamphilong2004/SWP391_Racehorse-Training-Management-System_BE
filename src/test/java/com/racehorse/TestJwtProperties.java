package com.racehorse;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.security.SecureRandom;
import java.util.Base64;

public final class TestJwtProperties {

    private static final String SIGNING_KEY = createSigningKey();

    private TestJwtProperties() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.signing-key", () -> SIGNING_KEY);
        registry.add("app.jwt.access-token-ttl-seconds", () -> "3600");
    }

    private static String createSigningKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
