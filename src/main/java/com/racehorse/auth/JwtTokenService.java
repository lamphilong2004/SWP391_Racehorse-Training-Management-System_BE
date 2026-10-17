package com.racehorse.auth;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final long ttlSeconds;
    private final Clock clock = Clock.systemUTC();

    public JwtTokenService(JwtEncoder jwtEncoder, JwtSettings settings) {
        this.jwtEncoder = jwtEncoder;
        try {
            this.ttlSeconds = Long.parseLong(settings.accessTokenTtlSeconds());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("JWT_ACCESS_TOKEN_TTL_SECONDS must be a positive integer.", ex);
        }
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("JWT_ACCESS_TOKEN_TTL_SECONDS must be a positive integer.");
        }
    }

    public IssuedToken issue(long userId, List<RoleCode> roles) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles.stream().map(role -> role.name()).toList())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(token, ttlSeconds);
    }

    public record IssuedToken(String value, long expiresIn) {
    }
}
