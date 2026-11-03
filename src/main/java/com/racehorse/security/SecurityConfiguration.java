package com.racehorse.security;

import com.racehorse.auth.JwtSettings;
import com.racehorse.auth.RoleCode;
import com.racehorse.auth.AuthenticationService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonSecurityHandlers securityHandlers,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CurrentAccountStatusFilter currentAccountStatusFilter,
            Environment environment) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityHandlers)
                        .accessDeniedHandler(securityHandlers))
                .authorizeHttpRequests(authorize -> {
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
                    authorize.requestMatchers("/ws", "/ws/**").permitAll();
                    if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
                        authorize.requestMatchers(
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    authorize.requestMatchers("/api/admin/**").hasRole(RoleCode.CLUB_MANAGER.name());
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(securityHandlers));
        http.addFilterAfter(currentAccountStatusFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationService.DummyPasswordHash dummyPasswordHash(PasswordEncoder passwordEncoder) {
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        return new AuthenticationService.DummyPasswordHash(
                passwordEncoder.encode(Base64.getEncoder().encodeToString(random)));
    }

    @Bean
    public SecretKey jwtSecretKey(JwtSettings settings) {
        if (settings.signingKey() == null || settings.signingKey().isBlank()) {
            throw new IllegalStateException("JWT_SIGNING_KEY is required and must be Base64-encoded.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(settings.signingKey());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SIGNING_KEY must be valid Base64.", ex);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SIGNING_KEY must decode to at least 32 bytes.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> claimsValidator = jwt -> {
            if (!hasValidSubject(jwt) || !hasValidRoles(jwt)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required token claims are invalid.", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), claimsValidator));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    private boolean hasValidSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            return false;
        }
        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean hasValidRoles(Jwt jwt) {
        Object claim = jwt.getClaims().get("roles");
        if (!(claim instanceof List<?> roles) || roles.isEmpty()) {
            return false;
        }
        if (jwt.getExpiresAt() == null) {
            return false;
        }
        return roles.stream().allMatch(role -> role instanceof String value && APPROVED_ROLE_CODES.contains(value));
    }

    private static final Set<String> APPROVED_ROLE_CODES = Arrays.stream(RoleCode.values())
            .map(role -> role.name())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
}
