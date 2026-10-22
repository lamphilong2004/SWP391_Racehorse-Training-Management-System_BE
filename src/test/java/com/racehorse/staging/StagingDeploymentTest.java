package com.racehorse.staging;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Configuration/security fixtures only. Not a SQL Server or hosted integration Gate. */
class StagingDeploymentTest {
    private static final String DOC_PASSWORD = "fixture-docs-password-for-tests";

    private static Map<String, Object> settings() {
        return new HashMap<>(Map.of(
                "spring.datasource.url", "jdbc:sqlserver://sql.example.test:1433;databaseName=fixture;encrypt=true;trustServerCertificate=false",
                "spring.datasource.username", "fixture-user", "spring.datasource.password", "fixture-password",
                "spring.jpa.hibernate.ddl-auto", "validate", "spring.sql.init.mode", "never",
                "app.cors.allowed-origins", "https://fe.example.test",
                "app.staging.docs-username", "fixture-docs", "app.staging.docs-password", DOC_PASSWORD));
    }

    private static MockEnvironment environment(Map<String, Object> settings) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("staging");
        environment.getPropertySources().addFirst(new MapPropertySource("fixture", settings));
        return environment;
    }

    @Test
    void permitsStrictTlsAndValidationOnly() {
        assertDoesNotThrow(() -> StagingDeploymentConfiguration.verify(environment(settings())));
    }

    @Test
    void rejectsWeakenedOrAmbiguousTlsWithoutPrintingTheUrl() {
        for (String suffix : new String[]{"encrypt=false;trustServerCertificate=false", "encrypt=true;trustServerCertificate=true",
                "encrypt=true", "encrypt=true;encrypt=false;trustServerCertificate=false"}) {
            var values = settings();
            values.put("spring.datasource.url", "jdbc:sqlserver://private-host;" + suffix);
            var failure = assertThrows(IllegalStateException.class, () -> StagingDeploymentConfiguration.verify(environment(values)));
            assertFalse(failure.getMessage().contains("private-host"));
        }
    }

    @Test
    void rejectsMissingCredentialsUnsafeOriginsAndSchemaMutation() {
        for (String key : new String[]{"spring.datasource.username", "spring.datasource.password", "app.staging.docs-username", "app.staging.docs-password"}) {
            var values = settings(); values.put(key, "");
            assertThrows(IllegalStateException.class, () -> StagingDeploymentConfiguration.verify(environment(values)));
        }
        for (String origin : new String[]{"*", "http://fe.example.test", "https://*.example.test", "https://user:password@fe.example.test", "https://fe.example.test/route"}) {
            var values = settings(); values.put("app.cors.allowed-origins", origin);
            assertThrows(IllegalStateException.class, () -> StagingDeploymentConfiguration.verify(environment(values)));
        }
        for (String key : new String[]{"spring.jpa.hibernate.ddl-auto", "spring.sql.init.mode"}) {
            var values = settings(); values.put(key, "create");
            assertThrows(IllegalStateException.class, () -> StagingDeploymentConfiguration.verify(environment(values)));
        }
    }

    @Test
    void rejectsCombiningStagingWithDevelopmentOrTests() {
        for (String profile : new String[]{"dev", "test"}) {
            var environment = environment(settings()); environment.setActiveProfiles("staging", profile);
            assertThrows(IllegalStateException.class, () -> StagingDeploymentConfiguration.verify(environment));
        }
    }

    @Test
    void documentationNeedsSeparateCredentialsAndTheyCannotAuthorizeBusinessRequests() throws Exception {
        try (var context = context()) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
            mvc.perform(get("/v3/api-docs").with(httpBasic("fixture-docs", "wrong"))).andExpect(status().isUnauthorized());
            mvc.perform(get("/v3/api-docs").with(httpBasic("fixture-docs", DOC_PASSWORD)))
                    .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
            mvc.perform(get("/api/private-probe").with(httpBasic("fixture-docs", DOC_PASSWORD)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void readinessIsReadOnlyAndHidesDatabaseFailures() throws Exception {
        try (var context = context()) {
            var jdbc = context.getBean(JdbcTemplate.class);
            // Begin the probe assertion after Spring's bean lifecycle initialization.
            clearInvocations(jdbc);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
            mvc.perform(get("/api/health/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UP"));
            when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("private database details"));
            mvc.perform(get("/api/health/ready")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.message").value("Database is unavailable."));
            verify(jdbc, times(2)).queryForObject("SELECT 1", Integer.class);
            verifyNoMoreInteractions(jdbc);
        }
    }

    private static AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.setEnvironment(environment(settings()));
        context.register(WebFixture.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({StagingDeploymentConfiguration.class, StagingReadinessController.class, Probe.class})
    static class WebFixture {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
        @Bean JdbcTemplate jdbcTemplate() { return mock(JdbcTemplate.class); }
        @Bean @Order(2) SecurityFilterChain businessBoundary(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll()).build();
        }
    }

    @RestController
    static class Probe {
        @GetMapping({"/v3/api-docs", "/swagger-ui/index.html", "/api/private-probe"})
        Map<String, String> document() { return Map.of("source", "security-test-fixture"); }
    }
}
