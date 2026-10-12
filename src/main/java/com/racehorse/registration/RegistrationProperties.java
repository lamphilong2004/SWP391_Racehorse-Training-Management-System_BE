package com.racehorse.registration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * DEC-008 §6 policy values (app.registration.*). Defaults are the approved values; override only by decision.
 * R1: cool-down 7 days after a rejection, at most 3 rejections. R8: 5 per 15 min per client IP, 3 per 24 h per email.
 */
@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(Duration rejectionCooldown, int maxRejections,
                                     int ipMaxAttempts, Duration ipWindow, int emailMaxAttempts, Duration emailWindow) {

    public RegistrationProperties {
        if (rejectionCooldown == null) rejectionCooldown = Duration.ofDays(7);
        if (maxRejections <= 0) maxRejections = 3;
        if (ipMaxAttempts <= 0) ipMaxAttempts = 5;
        if (ipWindow == null) ipWindow = Duration.ofMinutes(15);
        if (emailMaxAttempts <= 0) emailMaxAttempts = 3;
        if (emailWindow == null) emailWindow = Duration.ofHours(24);
    }
}
