package com.racehorse.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: JWT settings validation, email normalisation/masking, rate-limit window. */
class AuthUnitTest {

    @Test
    void jwtSecretMustBeConfiguredAndLongEnough() {
        assertThatThrownBy(() -> new JwtProperties("", Duration.ofHours(2), "x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(2), "x")).isInstanceOf(IllegalStateException.class);
        JwtProperties ok = new JwtProperties("a-key-that-is-long-enough-for-hs256-0123", Duration.ofHours(2), "racehorse-backend");
        assertThat(ok.toString()).doesNotContain("a-key-that-is-long-enough");
    }

    @Test
    void emailsAreNormalisedAndMasked() {
        assertThat(EmailAddresses.normalize("  Jane.Doe@Club.VN ")).isEqualTo("jane.doe@club.vn");
        assertThat(EmailAddresses.mask("jane.doe@club.vn")).isEqualTo("j***@club.vn");
        assertThat(EmailAddresses.mask("nonsense")).isEqualTo("***");
    }

    @Test
    void rateLimitWindowSlides() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-12T08:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(
                new SecurityProperties(false, new SecurityProperties.LoginRateLimit(10, Duration.ofMinutes(15))), clock);
        for (int i = 0; i < 10; i++) limiter.recordFailure("1.1.1.1", "a@b.c");
        assertThat(limiter.isBlocked("1.1.1.1", "a@b.c")).isTrue();
        assertThat(limiter.isBlocked("2.2.2.2", "a@b.c")).isFalse();
        clock.now = clock.now.plus(Duration.ofMinutes(15)).plusSeconds(1);
        assertThat(limiter.isBlocked("1.1.1.1", "a@b.c")).isFalse();
    }

    static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
