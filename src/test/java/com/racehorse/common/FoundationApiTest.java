package com.racehorse.common;

import com.racehorse.TestJwtProperties;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORE-001: health endpoint, error envelope for every error class, CORS from configuration, Swagger available. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FoundationApiTest.ProbeConfig.class)
class FoundationApiTest {

    private static final long AUTHENTICATED_TEST_USER_ID = 9000;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM dbo.user_role WHERE user_id = ?", AUTHENTICATED_TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM dbo.app_user WHERE id = ?", AUTHENTICATED_TEST_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO dbo.app_user (id, user_code, full_name, email, password_hash, account_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, AUTHENTICATED_TEST_USER_ID, "FOUNDATION-TEST", "Foundation Test",
                "foundation-test@example.test", "unused", "ACTIVE");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void healthReturnsEnvelopeWithUp() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void validationErrorReturns400Envelope() throws Exception {
        mvc.perform(post("/api/test-probe/validate").with(authenticatedJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @Test
    void malformedJsonReturns400Envelope() throws Exception {
        mvc.perform(post("/api/test-probe/validate").with(authenticatedJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void businessExceptionUsesItsCodeAndStatus() throws Exception {
        mvc.perform(get("/api/test-probe/conflict").with(authenticatedJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Horse code already exists."));
    }

    @Test
    void unknownRouteReturns404Envelope() throws Exception {
        mvc.perform(get("/api/does-not-exist").with(authenticatedJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMethodReturns405Envelope() throws Exception {
        mvc.perform(delete("/api/health").with(authenticatedJwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        mvc.perform(get("/api/test-probe/boom").with(authenticatedJwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."));
    }

    @Test
    void corsAllowsConfiguredOriginOnly() throws Exception {
        mvc.perform(options("/api/health").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/health").header("Origin", "http://evil.example").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Racehorse Training & Management System API"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedJwt() {
        return jwt().jwt(token -> token.subject(Long.toString(AUTHENTICATED_TEST_USER_ID)));
    }

    @TestConfiguration
    static class ProbeConfig {
        @RestController
        static class ProbeController {
            record Body(@NotBlank String name) {
            }

            @PostMapping("/api/test-probe/validate")
            String validate(@Valid @RequestBody Body body) {
                return body.name();
            }

            @GetMapping("/api/test-probe/conflict")
            String conflict() {
                throw new BusinessException(ErrorCode.CONFLICT, "Horse code already exists.");
            }

            @GetMapping("/api/test-probe/boom")
            String boom() {
                throw new IllegalStateException("secret internal detail");
            }

            @org.springframework.test.context.DynamicPropertySource
            static void jwtProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
                com.racehorse.TestJwtProperties.register(registry);
            }
        }
    }
}
