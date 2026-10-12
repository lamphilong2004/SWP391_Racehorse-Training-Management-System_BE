package com.racehorse.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** app.security.*: public Swagger (dev only) and the login failure rate limit (DEC-008 §6 R8). */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(boolean publicApiDocs, LoginRateLimit loginRateLimit) {

    public SecurityProperties {
        if (loginRateLimit == null) {
            loginRateLimit = new LoginRateLimit(10, Duration.ofMinutes(15));
        }
    }

    public record LoginRateLimit(int maxFailures, Duration window) {
        public LoginRateLimit {
            if (maxFailures <= 0) maxFailures = 10;
            if (window == null || window.isNegative() || window.isZero()) window = Duration.ofMinutes(15);
        }
    }
}
