package com.racehorse.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger / OpenAPI metadata. UI at /swagger-ui.html, JSON at /v3/api-docs. Contract files live in docs/api. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI racehorseOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .info(new Info()
                .title("Racehorse Training & Management System API")
                .version("0.1.0")
                .description("REST API for Flows 1–3 (Horse, Training, Medical) and shared infrastructure. "
                        + "All responses use the common envelope {success, data, error}."));
    }
}
