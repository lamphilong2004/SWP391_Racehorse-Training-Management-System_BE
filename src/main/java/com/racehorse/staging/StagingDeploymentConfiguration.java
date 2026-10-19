package com.racehorse.staging;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;

/** Staging-only deployment policy. Documentation credentials never authenticate business APIs. */
@Configuration(proxyBeanMethods = false)
@Profile("staging")
public class StagingDeploymentConfiguration {
    @Bean
    static BeanFactoryPostProcessor stagingConfigurationGuard(Environment environment) {
        return factory -> verify(environment);
    }

    static void verify(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equals("dev") || profile.equals("test")) {
                throw new IllegalStateException("Staging cannot be combined with dev or test profiles.");
            }
        }
        String url = required(environment, "spring.datasource.url");
        if (!url.startsWith("jdbc:sqlserver://")) throw new IllegalStateException("Staging requires SQL Server JDBC.");
        var properties = new HashMap<String, String>();
        for (String part : url.split(";")) {
            int separator = part.indexOf('=');
            if (separator < 0) continue;
            String key = part.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if (properties.putIfAbsent(key, part.substring(separator + 1).trim()) != null) {
                throw new IllegalStateException("Staging JDBC URL contains duplicate properties.");
            }
        }
        if (!"true".equalsIgnoreCase(properties.get("encrypt"))
                || !"false".equalsIgnoreCase(properties.get("trustservercertificate"))) {
            throw new IllegalStateException("Staging requires encrypt=true and trustServerCertificate=false.");
        }
        if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !"never".equals(environment.getProperty("spring.sql.init.mode"))) {
            throw new IllegalStateException("Staging permits schema validation only, without SQL initialization.");
        }
        required(environment, "spring.datasource.username");
        required(environment, "spring.datasource.password");
        required(environment, "app.staging.docs-username");
        if (required(environment, "app.staging.docs-password").length() < 20) {
            throw new IllegalStateException("STAGING_DOCS_PASSWORD must contain at least 20 characters.");
        }
        for (String origin : required(environment, "app.cors.allowed-origins").split(",", -1)) {
            try {
                URI parsed = URI.create(origin.trim());
                if (!"https".equals(parsed.getScheme()) || parsed.getHost() == null
                        || parsed.getHost().contains("*") || parsed.getRawUserInfo() != null
                        || parsed.getRawQuery() != null || parsed.getRawFragment() != null
                        || !(parsed.getRawPath() == null || parsed.getRawPath().isEmpty())) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Staging CORS requires exact HTTPS origins without credentials or paths.");
            }
        }
    }

    private static String required(Environment environment, String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required staging property: " + property);
        return value;
    }

    @Bean
    @Order(1)
    SecurityFilterChain stagingDocumentationChain(HttpSecurity http, PasswordEncoder encoder, Environment environment) throws Exception {
        var users = new InMemoryUserDetailsManager(User.withUsername(required(environment, "app.staging.docs-username"))
                .password(encoder.encode(required(environment, "app.staging.docs-password")))
                .roles("STAGING_DOCS").build());
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/api/health/ready")
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/health/ready").permitAll()
                        .anyRequest().hasRole("STAGING_DOCS"))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
