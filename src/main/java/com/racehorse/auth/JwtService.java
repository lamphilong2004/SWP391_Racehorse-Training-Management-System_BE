package com.racehorse.auth;

import com.racehorse.identity.AppUser;
import com.racehorse.identity.RoleCode;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** Issues stateless HS256 access tokens (no refresh token, no token store — physical-schema.md §9). */
@Service
public class JwtService {

    public record IssuedToken(String value, long expiresInSeconds) {
        /** Never prints the token. */
        @Override
        public String toString() {
            return "IssuedToken{expiresInSeconds=" + expiresInSeconds + "}";
        }
    }

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Callers must only pass a user for which {@link AppUser#canAuthenticate()} is true (DEC-008 §6 R5). */
    public IssuedToken issue(AppUser user) {
        if (!user.canAuthenticate()) {
            throw new IllegalStateException("A JWT is issued only for an ACTIVE user with a password.");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.expiresIn());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", user.roleCodes().stream().map(RoleCode::name).toList())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, properties.expiresIn().toSeconds());
    }
}
