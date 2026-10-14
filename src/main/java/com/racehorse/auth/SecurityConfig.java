package com.racehorse.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Spring Security for /api/** (DEC-008 §6 R10): stateless JWT bearer authentication; permitAll is exactly
 * login, register, forgot-password, reset-password, GET /api/health and — in the dev profile only — Swagger.
 * Every other request needs an authenticated ACTIVE user. Role rules use method security with the 7 role codes
 * ({@code @PreAuthorize("hasRole('CLUB_MANAGER')")}); there is no SYSTEM role.
 */
@Configuration
@EnableMethodSecurity
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {

    /** DEC-008 §6 R10 public auth endpoints (POST only). Register / reset are implemented by CORE-047 / CORE-050. */
    static final String[] PUBLIC_AUTH_POSTS = {
            "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password"
    };
    static final String[] API_DOCS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    /** BCrypt, strength 10 (DEC-008 §6 R2: same encoder for login and registration, strength ≥ 10). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.issuer())));
        return decoder;
    }

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProperties props,
                                           UserJwtAuthenticationConverter converter, SecurityErrorWriter errors) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API, no cookies
                .cors(Customizer.withDefaults())         // uses the MVC CORS mapping from CorsConfig (CORE-001)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)   // POST /api/auth/logout is a normal authenticated endpoint
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POSTS).permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
                    // DEMO INTEGRATION: WebSocket handshake; the JWT is verified on the STOMP CONNECT frame (flow2-stomp.md).
                    auth.requestMatchers("/ws", "/ws/**").permitAll();
                    if (props.publicApiDocs()) {
                        auth.requestMatchers(HttpMethod.GET, API_DOCS).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors.entryPoint())
                        .accessDeniedHandler(errors.accessDeniedHandler()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errors.entryPoint())
                        .accessDeniedHandler(errors.accessDeniedHandler()));
        return http.build();
    }

    private static SecretKey secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
