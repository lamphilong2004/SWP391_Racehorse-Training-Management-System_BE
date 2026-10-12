package com.racehorse.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DEC-008 §6 R10: with public-api-docs (dev profile) Swagger is reachable anonymously and documents bearerAuth. */
@SpringBootTest(properties = "app.security.public-api-docs=true")
@ActiveProfiles("test")
class DevSwaggerAccessTest {

    @Autowired WebApplicationContext context;

    @Test
    void apiDocsArePublicAndDescribeAuthEndpoints() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.operationId").value("login"))
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.operationId").value("logout"));
        // Everything else still needs a token.
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
